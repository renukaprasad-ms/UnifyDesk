package com.messaging.contacts.processing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.asyncjob.enums.AsyncJobType;
import com.messaging.asyncjob.service.AsyncJobService;
import com.messaging.common.exception.ForbiddenException;
import com.messaging.company.service.CompanyAuthorizationService;
import com.messaging.contacts.audience.ContactAudiencePermissions;
import com.messaging.contacts.consent.ConsentTypes.Channel;
import com.messaging.contacts.consent.ConsentTypes.PreferenceSource;
import com.messaging.contacts.consent.ConsentTypes.PreferenceState;
import com.messaging.contacts.processing.ContactImportMutationPort.ConsentColumnMapping;
import com.messaging.contacts.processing.ContactImportMutationPort.ConsentDeclaration;
import com.messaging.contacts.processing.ContactImportMutationPort.ImportMapping;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ContactProcessingAuthorizationPolicyTests {

  private CompanyAuthorizationService authorization;
  private ContactImportRepository importRepository;
  private ContactExportRepository exportRepository;
  private ContactBulkRepository bulkRepository;
  private ObjectMapper objectMapper;
  private ContactProcessingAuthorizationPolicy policy;

  @BeforeEach
  void setUp() {
    authorization = mock(CompanyAuthorizationService.class);
    importRepository = mock(ContactImportRepository.class);
    exportRepository = mock(ContactExportRepository.class);
    bulkRepository = mock(ContactBulkRepository.class);
    objectMapper = new ObjectMapper();
    policy =
        new ContactProcessingAuthorizationPolicy(
            authorization, importRepository, exportRepository, bulkRepository, objectMapper);
  }

  @Test
  void bulkDeleteRequiresDeleteInsteadOfModify() {
    policy.requireBulkQueue(
        7L,
        11L,
        new ContactBulkMutationPort.Operation("DELETE", null, null, null));

    verify(authorization).requireActiveMembership(7L, 11L);
    verify(authorization)
        .requirePermission(7L, 11L, ContactAudiencePermissions.DELETE);
    verify(authorization, never())
        .requirePermission(7L, 11L, ContactAudiencePermissions.MODIFY);
  }

  @Test
  void nonDestructiveBulkRequiresModify() {
    policy.requireBulkQueue(
        7L,
        11L,
        new ContactBulkMutationPort.Operation("ADD_TAG", 33L, null, null));

    verify(authorization)
        .requirePermission(7L, 11L, ContactAudiencePermissions.MODIFY);
    verify(authorization, never())
        .requirePermission(7L, 11L, ContactAudiencePermissions.DELETE);
  }

  @Test
  void importWithoutConsentRequiresOnlyImport() {
    policy.requireImportCommit(7L, 11L, importMapping(List.of()));

    verify(authorization)
        .requirePermission(7L, 11L, ContactAudiencePermissions.IMPORT);
    verify(authorization, never())
        .requirePermission(7L, 11L, ContactAudiencePermissions.CONSENT_MODIFY);
  }

  @Test
  void importOnlyRoleCannotQueueConsentImport() {
    ImportMapping mapping =
        importMapping(
            List.of(
                new ConsentColumnMapping(
                    Channel.EMAIL, 2, PreferenceSource.IMPORT, 3, null)));
    doThrow(new ForbiddenException("consent denied"))
        .when(authorization)
        .requirePermission(7L, 11L, ContactAudiencePermissions.CONSENT_MODIFY);

    org.junit.jupiter.api.Assertions.assertThrows(
        ForbiddenException.class,
        () -> policy.requireImportCommit(7L, 11L, mapping));

    verify(authorization)
        .requirePermission(7L, 11L, ContactAudiencePermissions.IMPORT);
    verify(authorization)
        .requirePermission(7L, 11L, ContactAudiencePermissions.CONSENT_MODIFY);
  }

  @Test
  void importWithConsentRequiresImportAndConsentModify() {
    ImportMapping mapping =
        importMapping(
            List.of(
                new ConsentColumnMapping(
                    Channel.EMAIL, 2, PreferenceSource.IMPORT, 3, null)));

    policy.requireImportCommit(7L, 11L, mapping);

    verify(authorization)
        .requirePermission(7L, 11L, ContactAudiencePermissions.IMPORT);
    verify(authorization)
        .requirePermission(7L, 11L, ContactAudiencePermissions.CONSENT_MODIFY);
  }

  @Test
  void bulkConsentDeclarationAlsoRequiresConsentModify() {
    ImportMapping mapping =
        importMapping(
            List.of(),
            List.of(
                new ConsentDeclaration(
                    Channel.WHATSAPP,
                    PreferenceState.OPTED_IN,
                    PreferenceSource.IMPORT,
                    "Existing customer list",
                    java.time.Instant.parse("2026-10-01T00:00:00Z"))));

    policy.requireImportCommit(7L, 11L, mapping);

    verify(authorization)
        .requirePermission(7L, 11L, ContactAudiencePermissions.IMPORT);
    verify(authorization)
        .requirePermission(7L, 11L, ContactAudiencePermissions.CONSENT_MODIFY);
  }

  @Test
  void revokedMembershipDeniesClaimAndReclaimWithStableReason() {
    AsyncJobService.ClaimedAsyncJob job =
        new AsyncJobService.ClaimedAsyncJob(1L, 11L, 7L, "99", null);
    doThrow(new ForbiddenException("revoked"))
        .when(authorization)
        .requireActiveMembership(7L, 11L);

    var first = policy.authorizeClaim(AsyncJobType.CONTACT_EXPORT, job);
    var reclaimed = policy.authorizeClaim(AsyncJobType.CONTACT_EXPORT, job);

    assertFalse(first.allowed());
    assertEquals(ContactProcessingAuthorizationPolicy.AUTHORIZATION_REVOKED, first.failureCode());
    assertEquals(first, reclaimed);
    verifyNoInteractions(exportRepository);
  }

  @Test
  void revokedConsentPermissionBlocksQueuedConsentImportBeforeWorker() throws Exception {
    ImportMapping mapping =
        importMapping(
            List.of(
                new ConsentColumnMapping(
                    Channel.SMS, 1, PreferenceSource.IMPORT, 2, null)));
    ContactImportRepository.ImportRow row =
        importRow(99L, 11L, 7L, objectMapper.writeValueAsString(mapping));
    when(importRepository.require(11L, 99L)).thenReturn(row);
    doThrow(new ForbiddenException("revoked"))
        .when(authorization)
        .requirePermission(7L, 11L, ContactAudiencePermissions.CONSENT_MODIFY);

    var decision =
        policy.authorizeClaim(
            AsyncJobType.CONTACT_IMPORT,
            new AsyncJobService.ClaimedAsyncJob(5L, 11L, 7L, "99", null));

    assertFalse(decision.allowed());
    assertEquals(
        ContactProcessingAuthorizationPolicy.AUTHORIZATION_REVOKED,
        decision.failureCode());
    verify(authorization)
        .requirePermission(7L, 11L, ContactAudiencePermissions.IMPORT);
    verify(authorization)
        .requirePermission(7L, 11L, ContactAudiencePermissions.CONSENT_MODIFY);
  }

  @Test
  void consentFreeQueuedImportNeedsNoConsentPermission() throws Exception {
    ImportMapping mapping = importMapping(List.of());
    when(importRepository.require(11L, 99L))
        .thenReturn(importRow(99L, 11L, 7L, objectMapper.writeValueAsString(mapping)));

    var decision =
        policy.authorizeClaim(
            AsyncJobType.CONTACT_IMPORT,
            new AsyncJobService.ClaimedAsyncJob(5L, 11L, 7L, "99", null));

    assertTrue(decision.allowed());
    verify(authorization, never())
        .requirePermission(7L, 11L, ContactAudiencePermissions.CONSENT_MODIFY);
  }

  @Test
  void claimedBulkDeleteUsesCurrentDeletePermission() {
    when(bulkRepository.require(11L, 99L))
        .thenReturn(
            new ContactBulkRepository.BulkRow(
                99L,
                11L,
                7L,
                "DELETE",
                "{}",
                "{}",
                "QUEUED",
                0,
                0,
                0,
                0,
                0,
                123L));

    var decision =
        policy.authorizeClaim(
            AsyncJobType.CONTACT_BULK,
            new AsyncJobService.ClaimedAsyncJob(5L, 11L, 7L, "99", null));

    assertTrue(decision.allowed());
    verify(authorization)
        .requirePermission(7L, 11L, ContactAudiencePermissions.DELETE);
    verify(authorization, never())
        .requirePermission(7L, 11L, ContactAudiencePermissions.MODIFY);
  }

  @Test
  void privacyPurgeIsNotReauthorizedAgainstRevokedActor() {
    var decision =
        policy.authorizeClaim(
            AsyncJobType.CONTACT_DELETE_PURGE,
            new AsyncJobService.ClaimedAsyncJob(5L, 11L, 7L, "99", null));

    assertTrue(decision.allowed());
    verifyNoInteractions(authorization, importRepository, exportRepository, bulkRepository);
  }

  private ImportMapping importMapping(List<ConsentColumnMapping> consentMappings) {
    return importMapping(consentMappings, List.of());
  }

  private ImportMapping importMapping(
      List<ConsentColumnMapping> consentMappings,
      List<ConsentDeclaration> consentDeclarations) {
    return new ImportMapping(
        null,
        null,
        null,
        null,
        null,
        0,
        null,
        null,
        Map.of(),
        consentMappings,
        consentDeclarations,
        null);
  }

  private ContactImportRepository.ImportRow importRow(
      long id, long companyId, long userId, String mappingJson) {
    return new ContactImportRepository.ImportRow(
        id,
        companyId,
        userId,
        "contacts.csv",
        "text/csv",
        null,
        "bucket",
        "object",
        10,
        "checksum",
        mappingJson,
        "mappingHash",
        "SKIP",
        null,
        "idem",
        "QUEUED",
        0,
        0,
        0,
        0,
        0,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null);
  }
}
