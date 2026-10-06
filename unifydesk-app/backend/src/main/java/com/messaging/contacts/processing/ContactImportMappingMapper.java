package com.messaging.contacts.processing;

import com.messaging.common.exception.BadRequestException;
import com.messaging.contacts.consent.ConsentTypes.PreferenceSource;
import com.messaging.contacts.consent.ConsentTypes.PreferenceState;
import com.messaging.contacts.processing.ContactImportMutationPort.ConsentColumnMapping;
import com.messaging.contacts.processing.ContactImportMutationPort.ConsentDeclaration;
import com.messaging.contacts.processing.ContactImportMutationPort.ImportMapping;
import com.messaging.contacts.processing.ContactProcessingApiModel.ConsentImportDeclaration;
import com.messaging.contacts.processing.ContactProcessingApiModel.ConsentImportMapping;
import com.messaging.contacts.processing.ContactProcessingApiModel.ImportFieldMapping;
import com.messaging.contacts.processing.ContactProcessingApiModel.ImportFieldTarget;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class ContactImportMappingMapper {

  public ImportMapping toDomain(
      List<String> headers,
      List<ImportFieldMapping> mappings,
      List<ConsentImportMapping> consentMappings,
      List<ConsentImportDeclaration> consentDeclarations) {
    Map<String, Integer> columns = columnIndexes(headers);
    List<ImportFieldMapping> safeMappings = mappings == null ? List.of() : mappings;
    if (safeMappings.size() > headers.size()) throw invalid();

    Integer displayName = null;
    Integer firstName = null;
    Integer lastName = null;
    Integer organization = null;
    Integer notes = null;
    Integer email = null;
    Integer phone = null;
    Integer tagNames = null;
    Map<Long, Integer> customFields = new HashMap<>();
    Set<String> assignedTargets = new HashSet<>();

    for (ImportFieldMapping mapping : safeMappings) {
      if (mapping == null || mapping.target() == null || mapping.target().type() == null) {
        throw invalid();
      }
      if (mapping.target().type() == ContactProcessingApiModel.ImportTargetType.IGNORE) {
        continue;
      }
      int column = requireColumn(columns, mapping.column());
      ImportFieldTarget target = mapping.target();
      switch (target.type()) {
        case BUILTIN -> {
          String key = require(target.field());
          requireUnique(assignedTargets, "BUILTIN:" + key);
          switch (key) {
            case "displayName" -> displayName = column;
            case "firstName" -> firstName = column;
            case "lastName" -> lastName = column;
            case "organization" -> organization = column;
            case "notes" -> notes = column;
            default -> throw invalid();
          }
        }
        case IDENTITY -> {
          String identityType = require(target.identityType());
          requireUnique(assignedTargets, "IDENTITY:" + identityType);
          if ("EMAIL".equals(identityType)) {
            email = column;
          } else if ("PHONE".equals(identityType)) {
            phone = column;
          } else {
            throw invalid();
          }
        }
        case TAG_NAMES -> {
          requireUnique(assignedTargets, "TAG_NAMES");
          tagNames = column;
        }
        case CUSTOM_FIELD -> {
          long fieldId = parseId(target.fieldId());
          requireUnique(assignedTargets, "CUSTOM_FIELD:" + fieldId);
          customFields.put(fieldId, column);
        }
        case IGNORE -> {
          // Handled above.
        }
      }
    }

    if (assignedTargets.isEmpty()) throw invalid();

    List<ConsentColumnMapping> consent = new ArrayList<>();
    List<ConsentImportMapping> safeConsent =
        consentMappings == null ? List.of() : consentMappings;
    if (safeConsent.size() > 3) throw invalid();
    Set<String> consentChannels = new HashSet<>();
    for (ConsentImportMapping mapping : safeConsent) {
      if (mapping == null
          || mapping.channel() == null
          || mapping.source() != PreferenceSource.IMPORT
          || !consentChannels.add(mapping.channel().name())) {
        throw invalid();
      }
      consent.add(
          new ConsentColumnMapping(
              mapping.channel(),
              requireColumn(columns, mapping.stateColumn()),
              mapping.source(),
              optionalColumn(columns, mapping.evidenceColumn()),
              optionalColumn(columns, mapping.occurredAtColumn())));
    }

    List<ConsentDeclaration> declarations = new ArrayList<>();
    List<ConsentImportDeclaration> safeDeclarations =
        consentDeclarations == null ? List.of() : consentDeclarations;
    if (safeDeclarations.size() > 3) throw invalid();
    for (ConsentImportDeclaration declaration : safeDeclarations) {
      if (declaration == null
          || declaration.channel() == null
          || declaration.state() != PreferenceState.OPTED_IN
          || declaration.source() != PreferenceSource.IMPORT
          || !declaration.confirmed()
          || declaration.evidence() == null
          || declaration.evidence().isBlank()
          || declaration.evidence().length() > 255
          || !consentChannels.add(declaration.channel().name())) {
        throw invalid();
      }
      declarations.add(
          new ConsentDeclaration(
              declaration.channel(),
              declaration.state(),
              declaration.source(),
              declaration.evidence().trim(),
              declaration.occurredAt() == null ? java.time.Instant.now() : declaration.occurredAt()));
    }

    return new ImportMapping(
        displayName,
        firstName,
        lastName,
        organization,
        notes,
        email,
        phone,
        tagNames,
        Map.copyOf(customFields),
        List.copyOf(consent),
        List.copyOf(declarations),
        null);
  }

  private Map<String, Integer> columnIndexes(List<String> headers) {
    if (headers == null || headers.isEmpty()) throw invalid();
    Map<String, Integer> result = new HashMap<>();
    for (int i = 0; i < headers.size(); i++) {
      String header = headers.get(i);
      if (header == null || header.isBlank() || result.putIfAbsent(header, i) != null) {
        throw invalid();
      }
    }
    return result;
  }

  private int requireColumn(Map<String, Integer> columns, String column) {
    Integer index = columns.get(require(column));
    if (index == null) throw invalid();
    return index;
  }

  private Integer optionalColumn(Map<String, Integer> columns, String column) {
    return column == null || column.isBlank() ? null : requireColumn(columns, column);
  }

  private void requireUnique(Set<String> targets, String key) {
    if (!targets.add(key)) throw invalid();
  }

  private String require(String value) {
    if (value == null || value.isBlank() || value.length() > 255) throw invalid();
    return value;
  }

  private long parseId(String value) {
    try {
      long parsed = Long.parseLong(require(value));
      if (parsed <= 0) throw invalid();
      return parsed;
    } catch (NumberFormatException exception) {
      throw invalid();
    }
  }

  private BadRequestException invalid() {
    return new BadRequestException("CONTACT_IMPORT_INVALID");
  }
}
