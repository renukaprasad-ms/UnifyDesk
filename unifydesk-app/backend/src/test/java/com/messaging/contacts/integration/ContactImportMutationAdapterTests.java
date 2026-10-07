package com.messaging.contacts.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.contacts.consent.ContactPreferenceService;
import com.messaging.contacts.consent.ConsentTypes.Channel;
import com.messaging.contacts.consent.ConsentTypes.PreferenceSource;
import com.messaging.contacts.consent.ConsentTypes.PreferenceState;
import com.messaging.contacts.core.domain.ContactTypes.ContactStatus;
import com.messaging.contacts.core.domain.CoreRows.ContactRow;
import com.messaging.contacts.core.repository.ContactListRepository;
import com.messaging.contacts.core.repository.ContactRepository;
import com.messaging.contacts.core.repository.CustomFieldRepository;
import com.messaging.contacts.core.repository.TagRepository;
import com.messaging.contacts.core.service.ContactQuotaGuard;
import com.messaging.contacts.core.service.CustomFieldValueCodec;
import com.messaging.contacts.core.service.IdentityNormalizer;
import com.messaging.contacts.core.service.IdentityNormalizer.NormalizedIdentity;
import com.messaging.contacts.processing.ContactImportMutationPort.ConsentDeclaration;
import com.messaging.contacts.processing.ContactImportMutationPort.DuplicateStrategy;
import com.messaging.contacts.processing.ContactImportMutationPort.ImportMapping;
import com.messaging.contacts.processing.ContactImportMutationPort.ImportRow;
import java.sql.Connection;
import java.sql.Savepoint;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

class ContactImportMutationAdapterTests {

  @Test
  void successfulRowUsesJdbcSavepointInsideBatchTransaction() throws Exception {
    Fixture fixture = fixture();
    when(fixture.contactRepository().insertContact(
            anyLong(), anyLong(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(contact(101L));

    var result =
        fixture.adapter().applyBatch(
            1L,
            2L,
            3L,
            emailOnlyMapping(),
            DuplicateStrategy.SKIP,
            null,
            List.of(new ImportRow(1L, List.of("alice@example.com"))));

    assertEquals(1L, result.succeeded());
    assertEquals(List.of(), result.failures());
    verify(fixture.connection()).setSavepoint();
    verify(fixture.connection()).releaseSavepoint(fixture.savepoint());
  }

  @Test
  void appliesConfirmedBulkConsentDeclarationToImportedContact() throws Exception {
    Fixture fixture = fixture();
    when(fixture.contactRepository().insertContact(
            anyLong(), anyLong(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(contact(101L));
    Instant occurredAt = Instant.parse("2026-10-01T00:00:00Z");

    ImportMapping mapping =
        new ImportMapping(
            null,
            null,
            null,
            null,
            null,
            0,
            null,
            null,
            Map.of(),
            List.of(),
            List.of(
                new ConsentDeclaration(
                    Channel.EMAIL,
                    PreferenceState.OPTED_IN,
                    PreferenceSource.IMPORT,
                    "Website form",
                    occurredAt)),
            null);

    fixture.adapter().applyBatch(
        1L,
        2L,
        3L,
        mapping,
        DuplicateStrategy.SKIP,
        null,
        List.of(new ImportRow(1L, List.of("alice@example.com"))));

    verify(fixture.preferenceService())
        .setFromImport(
            1L,
            101L,
            null,
            Channel.EMAIL,
            com.messaging.contacts.consent.ConsentTypes.Purpose.MARKETING,
            PreferenceState.OPTED_IN,
            "Website form",
            "Imported consent evidence",
            occurredAt,
            "import:2:row:1:EMAIL",
            2L);
  }

  @Test
  void unexpectedInfrastructureFailureAbortsBatchInsteadOfCreatingGenericRowFailure()
      throws Exception {
    Fixture fixture = fixture();
    when(fixture.contactRepository().insertContact(
            anyLong(), anyLong(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenThrow(new IllegalStateException("database unavailable"));

    assertThrows(
        IllegalStateException.class,
        () ->
            fixture.adapter().applyBatch(
                1L,
                2L,
                3L,
                emailOnlyMapping(),
                DuplicateStrategy.SKIP,
                null,
                List.of(new ImportRow(1L, List.of("alice@example.com")))));

    verify(fixture.connection()).rollback(fixture.savepoint());
  }

  private Fixture fixture() throws Exception {
    ContactRepository contactRepository = mock(ContactRepository.class);
    TagRepository tagRepository = mock(TagRepository.class);
    CustomFieldRepository customFieldRepository = mock(CustomFieldRepository.class);
    ContactListRepository listRepository = mock(ContactListRepository.class);
    IdentityNormalizer identityNormalizer = mock(IdentityNormalizer.class);
    CustomFieldValueCodec customFieldValueCodec = mock(CustomFieldValueCodec.class);
    ContactQuotaGuard quotaGuard = mock(ContactQuotaGuard.class);
    ContactPreferenceService preferenceService = mock(ContactPreferenceService.class);
    DataSource dataSource = mock(DataSource.class);
    Connection connection = mock(Connection.class);
    Savepoint savepoint = mock(Savepoint.class);

    when(tagRepository.list(1L)).thenReturn(List.of());
    when(identityNormalizer.normalize(
            com.messaging.contacts.core.domain.ContactTypes.IdentityType.EMAIL,
            null,
            "alice@example.com",
            null))
        .thenReturn(
            new NormalizedIdentity(
                com.messaging.contacts.core.domain.ContactTypes.IdentityType.EMAIL,
                null,
                "alice@example.com",
                "alice@example.com"));
    when(contactRepository.findActiveIdentityOwners(anyLong(), any())).thenReturn(Map.of());
    when(dataSource.getConnection()).thenReturn(connection);
    when(connection.getAutoCommit()).thenReturn(false);
    when(connection.setSavepoint()).thenReturn(savepoint);

    ContactImportMutationAdapter adapter =
        new ContactImportMutationAdapter(
            contactRepository,
            tagRepository,
            customFieldRepository,
            listRepository,
            identityNormalizer,
            customFieldValueCodec,
            quotaGuard,
            preferenceService,
            new ObjectMapper(),
            dataSource);

    return new Fixture(adapter, contactRepository, preferenceService, connection, savepoint);
  }

  private ImportMapping emailOnlyMapping() {
    return new ImportMapping(
        null, null, null, null, null, 0, null, null, Map.of(), List.of(), List.of(), null);
  }

  private ContactRow contact(long id) {
    Instant now = Instant.parse("2026-09-30T00:00:00Z");
    return new ContactRow(
        id,
        1L,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        "IMPORT",
        null,
        null,
        ContactStatus.ACTIVE,
        null,
        null,
        3L,
        now,
        now,
        null,
        0L);
  }

  private record Fixture(
      ContactImportMutationAdapter adapter,
      ContactRepository contactRepository,
      ContactPreferenceService preferenceService,
      Connection connection,
      Savepoint savepoint) {}
}
