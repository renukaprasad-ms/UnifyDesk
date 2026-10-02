package com.messaging.contacts.audience;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.NullNode;
import com.messaging.common.exception.BadRequestException;
import com.messaging.contacts.audience.SegmentApiModel.ContactSample;
import com.messaging.contacts.audience.SegmentApiModel.Field;
import com.messaging.contacts.audience.SegmentApiModel.Group;
import com.messaging.contacts.audience.SegmentApiModel.Node;
import com.messaging.contacts.audience.SegmentApiModel.Predicate;
import com.messaging.contacts.audience.SegmentApiModel.PreviewResponse;
import com.messaging.contacts.audience.SegmentApiModel.SegmentResponse;
import com.messaging.contacts.audience.SegmentApiModel.ValidationError;
import com.messaging.contacts.audience.SegmentApiModel.ValidationResponse;
import com.messaging.contacts.audience.SegmentFieldMetadataRepository.CustomFieldType;
import com.messaging.contacts.audience.SegmentFilter.BooleanValue;
import com.messaging.contacts.audience.SegmentFilter.DateValue;
import com.messaging.contacts.audience.SegmentFilter.FieldKind;
import com.messaging.contacts.audience.SegmentFilter.GroupNode;
import com.messaging.contacts.audience.SegmentFilter.IdListValue;
import com.messaging.contacts.audience.SegmentFilter.IdValue;
import com.messaging.contacts.audience.SegmentFilter.InstantValue;
import com.messaging.contacts.audience.SegmentFilter.NoValue;
import com.messaging.contacts.audience.SegmentFilter.NumberValue;
import com.messaging.contacts.audience.SegmentFilter.PredicateNode;
import com.messaging.contacts.audience.SegmentFilter.SegmentNode;
import com.messaging.contacts.audience.SegmentFilter.SegmentOperator;
import com.messaging.contacts.audience.SegmentFilter.SegmentValue;
import com.messaging.contacts.audience.SegmentFilter.TextListValue;
import com.messaging.contacts.audience.SegmentFilter.TextValue;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SegmentApiMapper {

  private final ObjectMapper objectMapper;
  private final ContactAudienceProperties properties;
  private final SegmentFieldMetadataRepository fieldMetadataRepository;

  public SegmentFilter toDomain(long companyId, int filterVersion, Node filter) {
    if (filterVersion != 1 || filter == null) {
      throw invalid();
    }
    try {
      if (objectMapper.writeValueAsBytes(filter).length > properties.getSegmentMaxBytes()) {
        throw invalid();
      }
    } catch (BadRequestException exception) {
      throw exception;
    } catch (Exception exception) {
      throw invalid();
    }
    return new SegmentFilter(1, toDomainNode(companyId, filter));
  }

  public Node toApi(SegmentFilter filter) {
    if (filter == null || filter.version() != 1 || filter.root() == null) {
      throw new IllegalStateException("Stored segment filter is invalid");
    }
    return toApiNode(filter.root());
  }

  public SegmentResponse toApi(SegmentService.SegmentResponse response) {
    String countState = response.cachedCount() == null ? "PENDING" : "CACHED";
    return new SegmentResponse(
        response.id(),
        response.name(),
        response.description(),
        response.filterVersion(),
        toApi(response.filter()),
        response.filterHash(),
        response.cachedCount(),
        countState,
        response.countComputedAt(),
        !"ACTIVE".equals(response.status()),
        response.version(),
        response.createdAt(),
        response.updatedAt());
  }

  public ValidationResponse valid(SegmentService.ValidationResult result) {
    return new ValidationResponse(
        true,
        toApi(result.normalizedFilter()),
        result.filterHash(),
        result.costClass().name(),
        List.of());
  }

  public ValidationResponse invalidValidation() {
    return new ValidationResponse(
        false,
        null,
        null,
        null,
        List.of(
            new ValidationError(
                "filter", "CONTACT_SEGMENT_INVALID", "Segment filter is invalid.")));
  }

  public PreviewResponse toApi(SegmentService.PreviewResult result) {
    List<ContactSample> sample =
        result.sample().stream()
            .map(
                row ->
                    new ContactSample(
                        Long.toString(row.id()),
                        row.displayName(),
                        row.firstName(),
                        row.lastName(),
                        row.organization(),
                        row.primaryEmail(),
                        row.primaryPhone(),
                        row.status(),
                        row.createdAt(),
                        row.updatedAt(),
                        row.lastActivityAt()))
            .toList();
    return new PreviewResponse(
        sample, result.count(), result.countState(), result.countComputedAt());
  }

  private SegmentNode toDomainNode(long companyId, Node node) {
    if (node instanceof Group group) {
      if (group.op() == null || group.children() == null || group.children().isEmpty()) {
        throw invalid();
      }
      List<SegmentNode> children = new ArrayList<>(group.children().size());
      for (Node child : group.children()) {
        if (child == null) throw invalid();
        children.add(toDomainNode(companyId, child));
      }
      return new GroupNode(group.op(), children);
    }
    if (node instanceof Predicate predicate) {
      return toDomainPredicate(companyId, predicate);
    }
    throw invalid();
  }

  private PredicateNode toDomainPredicate(long companyId, Predicate predicate) {
    if (predicate.field() == null
        || predicate.field().kind() == null
        || predicate.operator() == null) {
      throw invalid();
    }

    Field field = predicate.field();
    SegmentOperator operator = predicate.operator();
    return switch (field.kind()) {
      case BUILTIN_CONTACT ->
          new PredicateNode(
              new SegmentFilter.SegmentField(
                  FieldKind.BUILTIN_CONTACT, builtinKey(field.key()), null),
              operator,
              scalarOrList(predicate.value(), operator));
      case IDENTITY ->
          new PredicateNode(
              new SegmentFilter.SegmentField(
                  FieldKind.IDENTITY, identityKey(field.key()), null),
              operator,
              scalarOrList(predicate.value(), operator));
      case TAG ->
          new PredicateNode(
              new SegmentFilter.SegmentField(
                  FieldKind.TAG, null, requireId(predicate.value())),
              operator,
              new NoValue());
      case LIST ->
          new PredicateNode(
              new SegmentFilter.SegmentField(
                  FieldKind.LIST, null, requireId(predicate.value())),
              operator,
              new NoValue());
      case CUSTOM_FIELD -> customField(companyId, field, operator, predicate.value());
      case CHANNEL_PREFERENCE ->
          new PredicateNode(
              new SegmentFilter.SegmentField(
                  FieldKind.CHANNEL_PREFERENCE, preferenceChannel(field.key()), null),
              operator,
              new NoValue());
      case SUPPRESSION ->
          new PredicateNode(
              new SegmentFilter.SegmentField(FieldKind.SUPPRESSION, suppressionKey(field.key()), null),
              operator,
              new NoValue());
      case CREATED_AT ->
          new PredicateNode(
              new SegmentFilter.SegmentField(FieldKind.CREATED_AT, null, null),
              operator,
              temporalValue(predicate.value()));
      case LAST_ACTIVITY_AT ->
          new PredicateNode(
              new SegmentFilter.SegmentField(FieldKind.LAST_ACTIVITY_AT, null, null),
              operator,
              temporalValue(predicate.value()));
    };
  }

  private PredicateNode customField(
      long companyId, Field field, SegmentOperator operator, JsonNode value) {
    long fieldId = parseId(field.fieldId());
    CustomFieldType type =
        fieldMetadataRepository.requireActiveCustomField(companyId, fieldId).type();
    SegmentValue mapped =
        switch (type) {
          case TEXT, SINGLE_SELECT, MULTI_SELECT -> scalarOrList(value, operator);
          case NUMBER -> numberValue(value, operator);
          case BOOLEAN -> booleanValue(value, operator);
          case DATE -> dateValue(value, operator);
          case DATETIME -> temporalValue(value);
        };
    return new PredicateNode(
        new SegmentFilter.SegmentField(FieldKind.CUSTOM_FIELD, null, fieldId),
        operator,
        mapped);
  }

  private SegmentValue scalarOrList(JsonNode value, SegmentOperator operator) {
    if (noValue(operator)) return new NoValue();
    if (operator == SegmentOperator.IN || operator == SegmentOperator.NOT_IN) {
      if (value == null || !value.isArray() || value.isEmpty()) throw invalid();
      List<String> values = new ArrayList<>(value.size());
      for (JsonNode item : value) {
        if (!item.isTextual()) throw invalid();
        values.add(item.textValue());
      }
      return new TextListValue(values);
    }
    if (value == null || !value.isTextual()) throw invalid();
    return new TextValue(value.textValue());
  }

  private SegmentValue numberValue(JsonNode value, SegmentOperator operator) {
    if (noValue(operator)) return new NoValue();
    if (operator == SegmentOperator.IN || operator == SegmentOperator.NOT_IN) {
      if (value == null || !value.isArray() || value.isEmpty()) throw invalid();
      List<Long> values = new ArrayList<>(value.size());
      for (JsonNode item : value) {
        if (!item.canConvertToLong()) throw invalid();
        values.add(item.longValue());
      }
      return new IdListValue(values);
    }
    if (value == null || !value.isNumber()) throw invalid();
    return new NumberValue(value.decimalValue());
  }

  private SegmentValue booleanValue(JsonNode value, SegmentOperator operator) {
    if (noValue(operator)) return new NoValue();
    if (value == null || !value.isBoolean()) throw invalid();
    return new BooleanValue(value.booleanValue());
  }

  private SegmentValue dateValue(JsonNode value, SegmentOperator operator) {
    if (noValue(operator)) return new NoValue();
    if (value == null || !value.isTextual()) throw invalid();
    try {
      return new DateValue(LocalDate.parse(value.textValue()));
    } catch (RuntimeException exception) {
      throw invalid();
    }
  }

  private SegmentValue temporalValue(JsonNode value) {
    if (value == null || value instanceof NullNode) return new NoValue();
    if (!value.isTextual()) throw invalid();
    String raw = value.textValue();
    try {
      return new InstantValue(Instant.parse(raw));
    } catch (RuntimeException ignored) {
      try {
        return new InstantValue(LocalDate.parse(raw).atStartOfDay().toInstant(ZoneOffset.UTC));
      } catch (RuntimeException exception) {
        throw invalid();
      }
    }
  }

  private long requireId(JsonNode value) {
    if (value == null || !value.isTextual()) throw invalid();
    return parseId(value.textValue());
  }

  private long parseId(String value) {
    if (value == null || value.isBlank()) throw invalid();
    try {
      long parsed = Long.parseLong(value);
      if (parsed <= 0) throw invalid();
      return parsed;
    } catch (NumberFormatException exception) {
      throw invalid();
    }
  }

  private boolean noValue(SegmentOperator operator) {
    return operator == SegmentOperator.PRESENT
        || operator == SegmentOperator.ABSENT
        || operator == SegmentOperator.IS_OPTED_IN
        || operator == SegmentOperator.IS_OPTED_OUT
        || operator == SegmentOperator.IS_SUPPRESSED;
  }

  private String builtinKey(String key) {
    return switch (normalized(key)) {
      case "DISPLAYNAME" -> "DISPLAY_NAME";
      case "ORGANIZATION" -> "ORGANIZATION";
      case "JOBTITLE" -> "JOB_TITLE";
      case "COUNTRYCODE" -> "COUNTRY_CODE";
      case "LANGUAGE" -> "LANGUAGE";
      case "SOURCE" -> "SOURCE";
      default -> throw invalid();
    };
  }

  private String identityKey(String key) {
    return switch (normalized(key)) {
      case "EMAIL" -> "EMAIL";
      case "PHONE" -> "PHONE";
      default -> throw invalid();
    };
  }

  private String preferenceChannel(String key) {
    return switch (normalized(key)) {
      case "EMAILMARKETING" -> "EMAIL";
      case "SMSMARKETING" -> "SMS";
      case "WHATSAPPMARKETING" -> "WHATSAPP";
      default -> throw invalid();
    };
  }

  private String suppressionKey(String key) {
    if (!"ACTIVE".equals(normalized(key))) throw invalid();
    return "ANY";
  }

  private String normalized(String value) {
    if (value == null || value.isBlank() || value.length() > 64) throw invalid();
    return value.replace("_", "").replace("-", "").trim().toUpperCase(Locale.ROOT);
  }

  private Node toApiNode(SegmentNode node) {
    if (node instanceof GroupNode group) {
      return new Group(group.op(), group.children().stream().map(this::toApiNode).toList());
    }
    if (node instanceof PredicateNode predicate) {
      return toApiPredicate(predicate);
    }
    throw new IllegalStateException("Stored segment node is invalid");
  }

  private Predicate toApiPredicate(PredicateNode predicate) {
    SegmentFilter.SegmentField field = predicate.field();
    Field apiField =
        switch (field.kind()) {
          case BUILTIN_CONTACT -> new Field(field.kind(), apiBuiltinKey(field.key()), null);
          case IDENTITY -> new Field(field.kind(), field.key().toLowerCase(Locale.ROOT), null);
          case TAG -> new Field(field.kind(), "tagId", null);
          case LIST -> new Field(field.kind(), "listId", null);
          case CUSTOM_FIELD ->
              new Field(field.kind(), "customField", Long.toString(requireDomainId(field.id())));
          case CHANNEL_PREFERENCE ->
              new Field(field.kind(), apiPreferenceKey(field.key()), null);
          case SUPPRESSION -> new Field(field.kind(), "active", null);
          case CREATED_AT -> new Field(field.kind(), "createdAt", null);
          case LAST_ACTIVITY_AT -> new Field(field.kind(), "lastActivityAt", null);
        };
    JsonNode value =
        switch (field.kind()) {
          case TAG, LIST -> objectMapper.valueToTree(Long.toString(requireDomainId(field.id())));
          default -> apiValue(predicate.value());
        };
    return new Predicate(apiField, predicate.operator(), value);
  }

  private JsonNode apiValue(SegmentValue value) {
    if (value == null || value instanceof NoValue) return null;
    if (value instanceof TextValue text) return objectMapper.valueToTree(text.value());
    if (value instanceof NumberValue number) return objectMapper.valueToTree(number.value());
    if (value instanceof BooleanValue bool) return objectMapper.valueToTree(bool.value());
    if (value instanceof DateValue date) return objectMapper.valueToTree(date.value().toString());
    if (value instanceof InstantValue instant) {
      return objectMapper.valueToTree(instant.value().toString());
    }
    if (value instanceof IdValue id) return objectMapper.valueToTree(Long.toString(id.value()));
    if (value instanceof TextListValue texts) return objectMapper.valueToTree(texts.value());
    if (value instanceof IdListValue ids) {
      return objectMapper.valueToTree(ids.value());
    }
    throw new IllegalStateException("Stored segment value is invalid");
  }

  private long requireDomainId(Long value) {
    if (value == null || value <= 0) throw new IllegalStateException("Stored segment ID is invalid");
    return value;
  }

  private String apiBuiltinKey(String key) {
    return switch (key) {
      case "DISPLAY_NAME" -> "displayName";
      case "ORGANIZATION" -> "organization";
      case "JOB_TITLE" -> "jobTitle";
      case "COUNTRY_CODE" -> "countryCode";
      case "LANGUAGE" -> "language";
      case "SOURCE" -> "source";
      default -> throw new IllegalStateException("Stored segment field is invalid");
    };
  }

  private String apiPreferenceKey(String key) {
    return switch (key) {
      case "EMAIL" -> "emailMarketing";
      case "SMS" -> "smsMarketing";
      case "WHATSAPP" -> "whatsappMarketing";
      default -> throw new IllegalStateException("Stored preference field is invalid");
    };
  }

  private BadRequestException invalid() {
    return new BadRequestException("CONTACT_SEGMENT_INVALID");
  }
}
