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
  void mapsExtendedContactFieldsAndNamespacedExternalIdentity() {
    var domain =
        mapper.toDomain(
            List.of("External ID", "Source", "Country", "Job", "Timezone", "Language"),
            List.of(
                new ImportFieldMapping(
                    "External ID",
                    new ImportFieldTarget(
                        ImportTargetType.IDENTITY, null, "EXTERNAL", null, "Source")),
                new ImportFieldMapping(
                    "Source",
                    new ImportFieldTarget(
                        ImportTargetType.BUILTIN, "source", null, null)),
                new ImportFieldMapping(
                    "Country",
                    new ImportFieldTarget(
                        ImportTargetType.BUILTIN, "countryCode", null, null)),
                new ImportFieldMapping(
                    "Job",
                    new ImportFieldTarget(
                        ImportTargetType.BUILTIN, "jobTitle", null, null)),
                new ImportFieldMapping(
                    "Timezone",
                    new ImportFieldTarget(
                        ImportTargetType.BUILTIN, "timezone", null, null)),
                new ImportFieldMapping(
                    "Language",
                    new ImportFieldTarget(
                        ImportTargetType.BUILTIN, "language", null, null))),
            List.of());

    assertEquals(0, domain.externalIdColumn());
    assertEquals(1, domain.externalNamespaceColumn());
    assertEquals(1, domain.sourceColumn());
    assertEquals(2, domain.countryCodeColumn());
    assertEquals(3, domain.jobTitleColumn());
    assertEquals(4, domain.timezoneColumn());
    assertEquals(5, domain.languageColumn());
  }

  @Test
  void rejectsExternalIdentityWithoutNamespaceColumn() {
    assertThrows(
        RuntimeException.class,
        () ->
            mapper.toDomain(
                List.of("External ID"),
                List.of(
                    new ImportFieldMapping(
                        "External ID",
                        new ImportFieldTarget(
                            ImportTargetType.IDENTITY, null, "EXTERNAL", null, null))),
                List.of()));
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
