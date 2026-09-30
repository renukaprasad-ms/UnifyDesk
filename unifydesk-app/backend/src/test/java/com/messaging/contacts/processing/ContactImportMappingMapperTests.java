package com.messaging.contacts.processing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.messaging.contacts.consent.ConsentTypes.Channel;
import com.messaging.contacts.consent.ConsentTypes.PreferenceSource;
import com.messaging.contacts.processing.ContactProcessingApiModel.ConsentImportMapping;
import com.messaging.contacts.processing.ContactProcessingApiModel.ImportFieldMapping;
import com.messaging.contacts.processing.ContactProcessingApiModel.ImportFieldTarget;
import com.messaging.contacts.processing.ContactProcessingApiModel.ImportTargetType;
import java.util.List;
import org.junit.jupiter.api.Test;

class ContactImportMappingMapperTests {

  private final ContactImportMappingMapper mapper = new ContactImportMappingMapper();

  @Test
  void convertsColumnNamesToBoundedWorkerIndexes() {
    var domain =
        mapper.toDomain(
            List.of("Email", "First Name", "Consent", "Evidence"),
            List.of(
                new ImportFieldMapping(
                    "Email",
                    new ImportFieldTarget(
                        ImportTargetType.IDENTITY, null, "EMAIL", null)),
                new ImportFieldMapping(
                    "First Name",
                    new ImportFieldTarget(
                        ImportTargetType.BUILTIN, "firstName", null, null))),
            List.of(
                new ConsentImportMapping(
                    Channel.EMAIL,
                    "Consent",
                    PreferenceSource.IMPORT,
                    "Evidence",
                    null)));

    assertEquals(0, domain.emailColumn());
    assertEquals(1, domain.firstNameColumn());
    assertEquals(2, domain.consentMappings().getFirst().stateColumn());
    assertEquals(3, domain.consentMappings().getFirst().evidenceColumn());
  }

  @Test
  void rejectsUnknownColumnsAndDuplicateTargets() {
    assertThrows(
        RuntimeException.class,
        () ->
            mapper.toDomain(
                List.of("Email"),
                List.of(
                    new ImportFieldMapping(
                        "Missing",
                        new ImportFieldTarget(
                            ImportTargetType.IDENTITY, null, "EMAIL", null))),
                List.of()));

    assertThrows(
        RuntimeException.class,
        () ->
            mapper.toDomain(
                List.of("A", "B"),
                List.of(
                    new ImportFieldMapping(
                        "A",
                        new ImportFieldTarget(
                            ImportTargetType.IDENTITY, null, "EMAIL", null)),
                    new ImportFieldMapping(
                        "B",
                        new ImportFieldTarget(
                            ImportTargetType.IDENTITY, null, "EMAIL", null))),
                List.of()));
  }
}
