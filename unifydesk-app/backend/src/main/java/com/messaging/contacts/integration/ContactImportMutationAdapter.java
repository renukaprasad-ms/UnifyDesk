package com.messaging.contacts.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.messaging.common.exception.ApiException;
import com.messaging.contacts.consent.ContactPreferenceService;
import com.messaging.contacts.consent.ConsentTypes.PreferenceState;
import com.messaging.contacts.consent.ConsentTypes.Purpose;
import com.messaging.contacts.core.domain.ContactTypes.IdentityType;
import com.messaging.contacts.core.domain.ContactTypes.MembershipSource;
import com.messaging.contacts.core.domain.CoreRows.ContactRow;
import com.messaging.contacts.core.domain.CoreRows.CustomFieldDefinitionRow;
import com.messaging.contacts.core.repository.ContactListRepository;
import com.messaging.contacts.core.repository.ContactRepository;
import com.messaging.contacts.core.repository.ContactRepository.IdentityKey;
import com.messaging.contacts.core.repository.CustomFieldRepository;
import com.messaging.contacts.core.repository.TagRepository;
import com.messaging.contacts.core.service.ContactQuotaGuard;
import com.messaging.contacts.core.service.CustomFieldValueCodec;
import com.messaging.contacts.core.service.IdentityNormalizer;
import com.messaging.contacts.core.service.IdentityNormalizer.NormalizedIdentity;
import com.messaging.contacts.processing.ContactImportMutationPort;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Savepoint;
import java.sql.SQLException;
import java.text.Normalizer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ContactImportMutationAdapter implements ContactImportMutationPort {

  private final ContactRepository contactRepository;
  private final TagRepository tagRepository;
  private final CustomFieldRepository customFieldRepository;
  private final ContactListRepository listRepository;
  private final IdentityNormalizer identityNormalizer;
  private final CustomFieldValueCodec customFieldValueCodec;
  private final ContactQuotaGuard quotaGuard;
  private final ContactPreferenceService preferenceService;
  private final ObjectMapper objectMapper;
  private final DataSource dataSource;

  @Override
  @Transactional
  public BatchResult applyBatch(
      long companyId,
      long importId,
      long requestedByUserId,
      ImportMapping mapping,
      DuplicateStrategy duplicateStrategy,
      Long targetListId,
      List<ImportRow> rows) {
    if (mapping == null || duplicateStrategy == null || rows == null || rows.isEmpty()) {
      return new BatchResult(0, List.of());
    }

    Map<Long, CustomFieldDefinitionRow> fieldDefinitions =
        mapping.customFieldColumns() == null || mapping.customFieldColumns().isEmpty()
            ? Map.of()
            : customFieldRepository.findActiveDefinitions(
                companyId, mapping.customFieldColumns().keySet());
    if (fieldDefinitions.size()
        != (mapping.customFieldColumns() == null ? 0 : mapping.customFieldColumns().size())) {
      return failAll(rows, "CONTACT_FIELD_INVALID", "One or more custom fields are unavailable");
    }

    Map<String, Long> tagsByNormalizedName = new HashMap<>();
    tagRepository
        .list(companyId)
        .forEach(tag -> tagsByNormalizedName.put(tag.normalizedName(), tag.id()));

    if (targetListId != null) {
      try {
        listRepository.lockActive(companyId, targetListId);
      } catch (RuntimeException exception) {
        return failAll(rows, "CONTACT_NOT_FOUND", "Target list is unavailable");
      }
    }

    List<PreparedRow> preparedRows = new ArrayList<>(rows.size());
    List<NormalizedIdentity> allIdentities = new ArrayList<>();
    List<RowFailure> failures = new ArrayList<>();
    for (ImportRow row : rows) {
      try {
        PreparedRow prepared =
            prepareRow(companyId, mapping, row, fieldDefinitions, tagsByNormalizedName);
        preparedRows.add(prepared);
        allIdentities.addAll(prepared.identities());
      } catch (RuntimeException exception) {
        RowFailure rowFailure = failure(row.rowNumber(), exception);
        if ("CONTACT_IMPORT_ROW_FAILED".equals(rowFailure.code())) {
          throw exception;
        }
        failures.add(rowFailure);
      }
    }

    Map<IdentityKey, Long> existingOwners =
        new HashMap<>(contactRepository.findActiveIdentityOwners(companyId, allIdentities));
    long createCount =
        preparedRows.stream()
            .filter(row -> resolvedOwners(row, existingOwners).isEmpty())
            .count();
    if (createCount > 0) {
      quotaGuard.requireContactCapacity(companyId, createCount);
    }

    long succeeded = 0;
    Connection connection = DataSourceUtils.getConnection(dataSource);
    try {
      if (connection.getAutoCommit()) {
        throw new IllegalStateException("CONTACT_IMPORT_TRANSACTION_REQUIRED");
      }
      for (PreparedRow row : preparedRows) {
        Savepoint savepoint = createSavepoint(connection);
        try {
          AppliedRow applied =
              applyRow(
                  companyId,
                  importId,
                  requestedByUserId,
                  duplicateStrategy,
                  targetListId,
                  row,
                  existingOwners);
          releaseSavepoint(connection, savepoint);
          if (applied.succeeded()) {
            succeeded++;
            if (applied.contactId() != null) {
              for (NormalizedIdentity identity : row.identities()) {
                existingOwners.put(ContactRepository.key(identity), applied.contactId());
              }
            }
          }
        } catch (RuntimeException exception) {
          rollbackToSavepoint(connection, savepoint, exception);
          RowFailure rowFailure = failure(row.rowNumber(), exception);
          if ("CONTACT_IMPORT_ROW_FAILED".equals(rowFailure.code())) {
            throw exception;
          }
          failures.add(rowFailure);
        }
      }
    } catch (SQLException exception) {
      throw new IllegalStateException("CONTACT_IMPORT_TRANSACTION_FAILED", exception);
    } finally {
      DataSourceUtils.releaseConnection(connection, dataSource);
    }

    return new BatchResult(succeeded, List.copyOf(failures));
  }

  private Savepoint createSavepoint(Connection connection) {
    try {
      return connection.setSavepoint();
    } catch (SQLException exception) {
      throw new IllegalStateException("CONTACT_IMPORT_SAVEPOINT_UNAVAILABLE", exception);
    }
  }

  private void rollbackToSavepoint(
      Connection connection, Savepoint savepoint, RuntimeException original) {
    try {
      connection.rollback(savepoint);
      connection.releaseSavepoint(savepoint);
    } catch (SQLException rollbackFailure) {
      original.addSuppressed(rollbackFailure);
      throw new IllegalStateException("CONTACT_IMPORT_SAVEPOINT_ROLLBACK_FAILED", original);
    }
  }

  private void releaseSavepoint(Connection connection, Savepoint savepoint) {
    try {
      connection.releaseSavepoint(savepoint);
    } catch (SQLException exception) {
      throw new IllegalStateException("CONTACT_IMPORT_SAVEPOINT_RELEASE_FAILED", exception);
    }
  }

  private AppliedRow applyRow(
      long companyId,
      long importId,
      long userId,
      DuplicateStrategy strategy,
      Long targetListId,
      PreparedRow row,
      Map<IdentityKey, Long> existingOwners) {
    Set<Long> owners = resolvedOwners(row, existingOwners);
    if (owners.size() > 1) {
      throw new IllegalArgumentException("CONTACT_IDENTITY_CONFLICT");
    }

    Long contactId = owners.stream().findFirst().orElse(null);
    if (contactId != null && strategy == DuplicateStrategy.SKIP) {
      return new AppliedRow(true, contactId);
    }
    if (contactId != null && strategy == DuplicateStrategy.FAIL) {
      throw new IllegalArgumentException("CONTACT_IDENTITY_CONFLICT");
    }

    if (contactId == null) {
      ContactRow contact =
          contactRepository.insertContact(
              companyId,
              userId,
              row.firstName(),
              row.lastName(),
              row.displayName(),
              row.organization(),
              null,
              row.countryCode(),
              null,
              null,
              "IMPORT",
              row.notes(),
              null);
      contactId = contact.id();
      boolean emailPrimary = false;
      boolean phonePrimary = false;
      for (NormalizedIdentity identity : row.identities()) {
        boolean primary =
            switch (identity.type()) {
              case EMAIL -> {
                boolean value = !emailPrimary;
                emailPrimary = true;
                yield value;
              }
              case PHONE -> {
                boolean value = !phonePrimary;
                phonePrimary = true;
                yield value;
              }
              case EXTERNAL -> false;
            };
        contactRepository.insertIdentity(companyId, contactId, identity, primary);
      }
    } else {
      ContactRow current = contactRepository.lock(companyId, contactId);
      Map<String, Object> patch = new LinkedHashMap<>();
      putMapped(patch, "display_name", row.displayName(), row.displayNameMapped());
      putMapped(patch, "first_name", row.firstName(), row.firstNameMapped());
      putMapped(patch, "last_name", row.lastName(), row.lastNameMapped());
      putMapped(patch, "organization", row.organization(), row.organizationMapped());
      putMapped(patch, "notes", row.notes(), row.notesMapped());
      if (!patch.isEmpty()
          && contactRepository.patchContact(companyId, contactId, patch, current.version()) == 0) {
        throw new IllegalStateException("CONTACT_VERSION_CONFLICT");
      }
      for (NormalizedIdentity identity : row.identities()) {
        IdentityKey key = ContactRepository.key(identity);
        if (!existingOwners.containsKey(key)) {
          contactRepository.insertIdentity(companyId, contactId, identity, false);
        }
      }
    }

    if (!row.tagIds().isEmpty()) {
      tagRepository.addMemberships(companyId, contactId, row.tagIds(), userId);
    }
    if (row.customFieldPlan() != null) {
      customFieldRepository.deleteValues(
          companyId, contactId, row.customFieldPlan().deletes());
      customFieldRepository.upsertValues(
          companyId, contactId, row.customFieldPlan().values());
    }
    if (targetListId != null) {
      listRepository.addMembers(
          companyId,
          targetListId,
          List.of(contactId),
          MembershipSource.IMPORT,
          Long.toString(importId),
          userId);
    }

    for (PreparedConsent consent : row.consents()) {
      preferenceService.setFromImport(
          companyId,
          contactId,
          null,
          consent.channel(),
          Purpose.MARKETING,
          consent.state(),
          consent.evidence(),
          "Imported consent evidence",
          consent.occurredAt(),
          "import:" + importId + ":row:" + row.rowNumber() + ":" + consent.channel().name(),
          importId);
    }
    return new AppliedRow(true, contactId);
  }

  private PreparedRow prepareRow(
      long companyId,
      ImportMapping mapping,
      ImportRow row,
      Map<Long, CustomFieldDefinitionRow> definitions,
      Map<String, Long> tagsByNormalizedName) {
    List<String> cells = row.cells();
    List<NormalizedIdentity> identities = new ArrayList<>(2);
    if (mapping.emailColumn() != null) {
      String raw = cell(cells, mapping.emailColumn());
      if (raw != null) {
        identities.add(identityNormalizer.normalize(IdentityType.EMAIL, null, raw, null));
      }
    }
    if (mapping.phoneColumn() != null) {
      String raw = cell(cells, mapping.phoneColumn());
      if (raw != null) {
        identities.add(
            identityNormalizer.normalize(
                IdentityType.PHONE, null, raw, mapping.defaultCountryCode()));
      }
    }
    if (identities.isEmpty()) {
      throw new IllegalArgumentException("CONTACT_INVALID_IDENTITY");
    }

    Set<Long> tagIds = new LinkedHashSet<>();
    if (mapping.tagNamesColumn() != null) {
      String raw = cell(cells, mapping.tagNamesColumn());
      if (raw != null) {
        for (String tagName : raw.split("[,;]")) {
          String normalized = normalizedName(tagName);
          if (normalized == null) continue;
          Long tagId = tagsByNormalizedName.get(normalized);
          if (tagId == null) {
            throw new IllegalArgumentException("CONTACT_TAG_NOT_FOUND");
          }
          tagIds.add(tagId);
        }
      }
    }

    Map<String, JsonNode> fields = new LinkedHashMap<>();
    if (mapping.customFieldColumns() != null) {
      for (var entry : mapping.customFieldColumns().entrySet()) {
        CustomFieldDefinitionRow definition = definitions.get(entry.getKey());
        String raw = cell(cells, entry.getValue());
        fields.put(
            Long.toString(entry.getKey()),
            raw == null ? objectMapper.nullNode() : customFieldNode(definition, raw));
      }
    }
    CustomFieldValueCodec.Plan plan =
        fields.isEmpty() ? null : customFieldValueCodec.validatePrepared(definitions, fields);

    List<PreparedConsent> consents = new ArrayList<>();
    if (mapping.consentMappings() != null) {
      for (ConsentColumnMapping consent : mapping.consentMappings()) {
        String stateValue = cell(cells, consent.stateColumn());
        if (stateValue == null) continue;
        PreferenceState state = parsePreferenceState(stateValue);
        String evidence =
            consent.evidenceColumn() == null ? null : cell(cells, consent.evidenceColumn());
        if (state == PreferenceState.OPTED_IN && (evidence == null || evidence.isBlank())) {
          throw new IllegalArgumentException("CONTACT_CONSENT_EVIDENCE_REQUIRED");
        }
        Instant occurredAt =
            consent.occurredAtColumn() == null
                ? Instant.now()
                : parseInstant(cell(cells, consent.occurredAtColumn()));
        consents.add(
            new PreparedConsent(
                consent.channel(), state, consent.source(), evidence, occurredAt));
      }
    }
    if (mapping.consentDeclarations() != null) {
      for (ConsentDeclaration declaration : mapping.consentDeclarations()) {
        consents.add(
            new PreparedConsent(
                declaration.channel(),
                declaration.state(),
                declaration.source(),
                declaration.evidence(),
                declaration.occurredAt()));
      }
    }

    return new PreparedRow(
        row.rowNumber(),
        mapped(cells, mapping.displayNameColumn()),
        mapping.displayNameColumn() != null,
        mapped(cells, mapping.firstNameColumn()),
        mapping.firstNameColumn() != null,
        mapped(cells, mapping.lastNameColumn()),
        mapping.lastNameColumn() != null,
        mapped(cells, mapping.organizationColumn()),
        mapping.organizationColumn() != null,
        mapped(cells, mapping.notesColumn()),
        mapping.notesColumn() != null,
        mapping.defaultCountryCode(),
        List.copyOf(identities),
        Set.copyOf(tagIds),
        plan,
        List.copyOf(consents));
  }

  private JsonNode customFieldNode(CustomFieldDefinitionRow definition, String raw) {
    if (definition == null) throw new IllegalArgumentException("CONTACT_FIELD_INVALID");
    try {
      return switch (definition.type()) {
        case TEXT, SINGLE_SELECT -> objectMapper.getNodeFactory().textNode(raw);
        case NUMBER -> objectMapper.valueToTree(new BigDecimal(raw.trim()));
        case BOOLEAN -> {
          if (!raw.equalsIgnoreCase("true") && !raw.equalsIgnoreCase("false")) {
            throw new IllegalArgumentException("CONTACT_FIELD_INVALID");
          }
          yield objectMapper.getNodeFactory().booleanNode(Boolean.parseBoolean(raw));
        }
        case DATE, DATETIME -> objectMapper.getNodeFactory().textNode(raw.trim());
        case MULTI_SELECT -> {
          ArrayNode array = objectMapper.createArrayNode();
          for (String item : raw.split("[|;]")) {
            String value = item.trim();
            if (!value.isEmpty()) array.add(value);
          }
          yield array;
        }
      };
    } catch (RuntimeException exception) {
      throw new IllegalArgumentException("CONTACT_FIELD_INVALID", exception);
    }
  }

  private PreferenceState parsePreferenceState(String raw) {
    String normalized = raw.trim().toUpperCase(Locale.ROOT);
    return switch (normalized) {
      case "OPTED_IN", "OPTIN", "YES", "TRUE", "1" -> PreferenceState.OPTED_IN;
      case "OPTED_OUT", "OPTOUT", "NO", "FALSE", "0" -> PreferenceState.OPTED_OUT;
      case "UNKNOWN" -> PreferenceState.UNKNOWN;
      default -> throw new IllegalArgumentException("CONTACT_FIELD_INVALID");
    };
  }

  private Instant parseInstant(String raw) {
    try {
      return raw == null || raw.isBlank() ? Instant.now() : Instant.parse(raw.trim());
    } catch (RuntimeException exception) {
      throw new IllegalArgumentException("CONTACT_FIELD_INVALID", exception);
    }
  }

  private Set<Long> resolvedOwners(
      PreparedRow row, Map<IdentityKey, Long> existingOwners) {
    Set<Long> result = new LinkedHashSet<>();
    for (NormalizedIdentity identity : row.identities()) {
      Long owner = existingOwners.get(ContactRepository.key(identity));
      if (owner != null) result.add(owner);
    }
    return result;
  }

  private void putMapped(
      Map<String, Object> patch, String column, String value, boolean mapped) {
    if (mapped) patch.put(column, value);
  }

  private String mapped(List<String> cells, Integer index) {
    return index == null ? null : cell(cells, index);
  }

  private String cell(List<String> cells, int index) {
    if (index < 0 || index >= cells.size()) {
      throw new IllegalArgumentException("CONTACT_IMPORT_INVALID");
    }
    String value = cells.get(index);
    if (value == null) return null;
    value = value.trim();
    return value.isEmpty() ? null : value;
  }

  private String normalizedName(String raw) {
    if (raw == null) return null;
    String value = Normalizer.normalize(raw.trim(), Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
    return value.isBlank() ? null : value;
  }

  private BatchResult failAll(List<ImportRow> rows, String code, String message) {
    return new BatchResult(
        0,
        rows.stream()
            .map(row -> new RowFailure(row.rowNumber(), code, message))
            .toList());
  }

  private RowFailure failure(long rowNumber, RuntimeException exception) {
    String code =
        exception instanceof ApiException
            ? exception.getMessage()
            : stableCode(exception.getMessage());
    return new RowFailure(
        rowNumber,
        code,
        switch (code) {
          case "CONTACT_IDENTITY_CONFLICT" -> "Identity already belongs to another contact";
          case "CONTACT_INVALID_IDENTITY" -> "Email or phone is invalid";
          case "CONTACT_FIELD_INVALID" -> "A mapped field value is invalid";
          case "CONTACT_TAG_NOT_FOUND" -> "A mapped tag does not exist";
          case "CONTACT_CONSENT_EVIDENCE_REQUIRED" -> "Opt-in consent evidence is required";
          case "CONTACT_VERSION_CONFLICT" -> "Contact changed while the row was processed";
          default -> "Row could not be imported";
        });
  }

  private String stableCode(String message) {
    if (message == null || !message.matches("[A-Z0-9_]{3,80}")) {
      return "CONTACT_IMPORT_ROW_FAILED";
    }
    return message;
  }

  private record AppliedRow(boolean succeeded, Long contactId) {}

  private record PreparedConsent(
      com.messaging.contacts.consent.ConsentTypes.Channel channel,
      PreferenceState state,
      com.messaging.contacts.consent.ConsentTypes.PreferenceSource source,
      String evidence,
      Instant occurredAt) {}

  private record PreparedRow(
      long rowNumber,
      String displayName,
      boolean displayNameMapped,
      String firstName,
      boolean firstNameMapped,
      String lastName,
      boolean lastNameMapped,
      String organization,
      boolean organizationMapped,
      String notes,
      boolean notesMapped,
      String countryCode,
      List<NormalizedIdentity> identities,
      Set<Long> tagIds,
      CustomFieldValueCodec.Plan customFieldPlan,
      List<PreparedConsent> consents) {}
}
