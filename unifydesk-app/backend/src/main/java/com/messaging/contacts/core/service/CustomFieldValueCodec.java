package com.messaging.contacts.core.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.contacts.core.domain.ContactTypes.CustomFieldType;
import com.messaging.contacts.core.domain.CoreRows.CustomFieldDefinitionRow;
import com.messaging.contacts.core.domain.CoreRows.CustomFieldValueRow;
import com.messaging.contacts.core.exception.ContactCoreException;
import com.messaging.contacts.core.repository.CustomFieldRepository;
import com.messaging.contacts.core.repository.CustomFieldRepository.TypedValue;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CustomFieldValueCodec {

  private static final int MAX_TEXT = 4000;
  private static final int MAX_OPTIONS = 100;
  private static final int MAX_OPTION_LENGTH = 200;

  private final CustomFieldRepository repository;
  private final ObjectMapper objectMapper;

  public Plan validate(long companyId, Map<String, JsonNode> supplied) {
    if (supplied == null || supplied.isEmpty()) {
      return new Plan(List.of(), List.of());
    }
    if (supplied.size() > 100) {
      throw ContactCoreException.invalidField();
    }

    Map<Long, JsonNode> byId = new LinkedHashMap<>();
    for (Map.Entry<String, JsonNode> entry : supplied.entrySet()) {
      long id = parseId(entry.getKey());
      if (byId.put(id, entry.getValue()) != null) {
        throw ContactCoreException.invalidField();
      }
    }

    Map<Long, CustomFieldDefinitionRow> definitions =
        repository.findActiveDefinitions(companyId, byId.keySet());
    if (definitions.size() != byId.size()) {
      throw ContactCoreException.invalidField();
    }

    List<TypedValue> values = new ArrayList<>();
    List<Long> deletes = new ArrayList<>();
    for (Map.Entry<Long, JsonNode> entry : byId.entrySet()) {
      JsonNode node = entry.getValue();
      if (node == null || node.isNull()) {
        deletes.add(entry.getKey());
        continue;
      }
      values.add(toTyped(definitions.get(entry.getKey()), node));
    }
    return new Plan(List.copyOf(values), List.copyOf(deletes));
  }

  public String validateOptions(CustomFieldType type, List<String> options) {
    boolean select = type == CustomFieldType.SINGLE_SELECT || type == CustomFieldType.MULTI_SELECT;
    if (!select) {
      if (options != null && !options.isEmpty()) {
        throw ContactCoreException.invalidField();
      }
      return null;
    }
    if (options == null || options.isEmpty() || options.size() > MAX_OPTIONS) {
      throw ContactCoreException.invalidField();
    }
    List<String> normalized = normalizeOptions(options);
    try {
      return objectMapper.writeValueAsString(normalized);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Unable to serialize Contact custom-field options", exception);
    }
  }

  public List<String> readOptions(String optionsJson) {
    if (optionsJson == null || optionsJson.isBlank()) {
      return List.of();
    }
    try {
      return objectMapper.readValue(
          optionsJson,
          objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Invalid stored Contact custom-field options", exception);
    }
  }

  public JsonNode toJson(CustomFieldValueRow row) {
    if (row.textValue() != null) return objectMapper.valueToTree(row.textValue());
    if (row.numberValue() != null) return objectMapper.valueToTree(row.numberValue());
    if (row.booleanValue() != null) return objectMapper.valueToTree(row.booleanValue());
    if (row.dateValue() != null) return objectMapper.valueToTree(row.dateValue().toString());
    if (row.datetimeValue() != null) return objectMapper.valueToTree(row.datetimeValue().toString());
    return objectMapper.valueToTree(row.textArrayValue());
  }

  public String indexability(CustomFieldType type) {
    return switch (type) {
      case TEXT -> "EXACT_PREFIX";
      case NUMBER, DATE, DATETIME -> "EXACT_RANGE";
      case BOOLEAN, SINGLE_SELECT -> "EXACT";
      case MULTI_SELECT -> "MEMBERSHIP";
    };
  }

  private TypedValue toTyped(CustomFieldDefinitionRow definition, JsonNode node) {
    try {
      return switch (definition.type()) {
        case TEXT -> {
          if (!node.isTextual() || node.textValue().length() > MAX_TEXT) invalid();
          yield new TypedValue(
              definition.id(), node.textValue(), null, null, null, null, null);
        }
        case NUMBER -> {
          if (!node.isNumber()) invalid();
          yield new TypedValue(
              definition.id(), null, node.decimalValue(), null, null, null, null);
        }
        case BOOLEAN -> {
          if (!node.isBoolean()) invalid();
          yield new TypedValue(
              definition.id(), null, null, node.booleanValue(), null, null, null);
        }
        case DATE -> {
          if (!node.isTextual()) invalid();
          LocalDate date = LocalDate.parse(node.textValue());
          yield new TypedValue(definition.id(), null, null, null, date, null, null);
        }
        case DATETIME -> {
          if (!node.isTextual()) invalid();
          Instant instant = Instant.parse(node.textValue());
          yield new TypedValue(definition.id(), null, null, null, null, instant, null);
        }
        case SINGLE_SELECT -> {
          if (!node.isTextual()) invalid();
          String value = node.textValue();
          if (!readOptions(definition.optionsJson()).contains(value)) invalid();
          yield new TypedValue(definition.id(), value, null, null, null, null, null);
        }
        case MULTI_SELECT -> {
          if (!node.isArray() || node.size() > MAX_OPTIONS) invalid();
          Set<String> allowed = Set.copyOf(readOptions(definition.optionsJson()));
          List<String> values = new ArrayList<>();
          Set<String> seen = new HashSet<>();
          for (JsonNode item : node) {
            if (!item.isTextual()
                || !allowed.contains(item.textValue())
                || !seen.add(item.textValue())) {
              invalid();
            }
            values.add(item.textValue());
          }
          yield new TypedValue(
              definition.id(), null, null, null, null, null, List.copyOf(values));
        }
      };
    } catch (DateTimeParseException | ArithmeticException exception) {
      throw ContactCoreException.invalidField();
    }
  }

  private List<String> normalizeOptions(Collection<String> options) {
    List<String> result = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    for (String option : options) {
      if (option == null) invalid();
      String value = option.trim();
      if (value.isBlank() || value.length() > MAX_OPTION_LENGTH || !seen.add(value)) invalid();
      result.add(value);
    }
    return List.copyOf(result);
  }

  private long parseId(String value) {
    try {
      long id = Long.parseLong(value);
      if (id <= 0) invalid();
      return id;
    } catch (NumberFormatException exception) {
      throw ContactCoreException.invalidField();
    }
  }

  private void invalid() {
    throw ContactCoreException.invalidField();
  }

  public record Plan(List<TypedValue> values, List<Long> deletes) {}
}
