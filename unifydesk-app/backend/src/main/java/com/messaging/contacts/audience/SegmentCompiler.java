package com.messaging.contacts.audience;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.common.exception.BadRequestException;
import com.messaging.contacts.audience.SegmentFieldMetadataRepository.CustomFieldType;
import com.messaging.contacts.audience.SegmentFilter.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SegmentCompiler {

  private static final Map<String, BuiltinField> BUILTINS =
      Map.of(
          "DISPLAY_NAME", new BuiltinField("c.display_name", ValueKind.TEXT),
          "ORGANIZATION", new BuiltinField("c.organization", ValueKind.TEXT),
          "JOB_TITLE", new BuiltinField("c.job_title", ValueKind.TEXT),
          "COUNTRY_CODE", new BuiltinField("c.country_code", ValueKind.TEXT),
          "LANGUAGE", new BuiltinField("c.language", ValueKind.TEXT),
          "SOURCE", new BuiltinField("c.source", ValueKind.TEXT));

  private final ObjectMapper objectMapper;
  private final ContactAudienceProperties properties;
  private final SegmentFieldMetadataRepository fieldMetadataRepository;

  public CompiledSegment compile(long companyId, SegmentFilter filter) {
    validateEnvelope(filter);
    CompileState state = new CompileState(companyId);
    String expression = compileNode(filter.root(), 1, state);
    if (state.predicateCount == 0) {
      throw invalid();
    }
    String where = "c.company_id = :companyId AND c.status = 'ACTIVE' AND (" + expression + ")";
    state.params.put("companyId", companyId);
    return new CompiledSegment(
        where, Map.copyOf(state.params), state.cost, hash(filter), state.predicateCount);
  }

  private void validateEnvelope(SegmentFilter filter) {
    if (filter == null || filter.version() != 1 || filter.root() == null) {
      throw invalid();
    }
    try {
      int bytes = objectMapper.writeValueAsBytes(filter).length;
      if (bytes > properties.getSegmentMaxBytes()) {
        throw invalid();
      }
    } catch (JsonProcessingException exception) {
      throw invalid();
    }
  }

  private String compileNode(SegmentNode node, int depth, CompileState state) {
    if (depth > properties.getSegmentMaxDepth()) {
      throw invalid();
    }
    if (node instanceof GroupNode group) {
      if (group.op() == null || group.children() == null || group.children().isEmpty()) {
        throw invalid();
      }
      List<String> children = new ArrayList<>(group.children().size());
      for (SegmentNode child : group.children()) {
        children.add("(" + compileNode(child, depth + 1, state) + ")");
      }
      return String.join(group.op() == GroupOperator.AND ? " AND " : " OR ", children);
    }
    if (node instanceof PredicateNode predicate) {
      state.predicateCount++;
      if (state.predicateCount > properties.getSegmentMaxPredicates()) {
        throw invalid();
      }
      return compilePredicate(predicate, state);
    }
    throw invalid();
  }

  private String compilePredicate(PredicateNode predicate, CompileState state) {
    if (predicate.field() == null
        || predicate.field().kind() == null
        || predicate.operator() == null) {
      throw invalid();
    }
    return switch (predicate.field().kind()) {
      case BUILTIN_CONTACT -> compileBuiltin(predicate, state);
      case CREATED_AT -> compileTemporal("c.created_at", predicate, state);
      case LAST_ACTIVITY_AT -> compileTemporal("c.last_activity_at", predicate, state);
      case IDENTITY -> compileIdentity(predicate, state);
      case TAG -> compileTag(predicate, state);
      case LIST -> compileList(predicate, state);
      case CUSTOM_FIELD -> compileCustomField(predicate, state);
      case CHANNEL_PREFERENCE -> compilePreference(predicate, state);
      case SUPPRESSION -> compileSuppression(predicate, state);
    };
  }

  private String compileBuiltin(PredicateNode predicate, CompileState state) {
    String key = normalizedKey(predicate.field().key());
    BuiltinField field = BUILTINS.get(key);
    if (field == null) throw invalid();
    return compileScalar(field.sql(), field.kind(), predicate.operator(), predicate.value(), state);
  }

  private String compileTemporal(String sql, PredicateNode predicate, CompileState state) {
    if (predicate.field().id() != null || predicate.field().key() != null) {
      throw invalid();
    }
    return compileScalar(sql, ValueKind.DATETIME, predicate.operator(), predicate.value(), state);
  }

  private String compileIdentity(PredicateNode predicate, CompileState state) {
    String identityType = normalizedKey(predicate.field().key());
    if (!identityType.equals("EMAIL") && !identityType.equals("PHONE")) {
      throw invalid();
    }
    SegmentOperator operator = predicate.operator();
    if (operator == SegmentOperator.PRESENT || operator == SegmentOperator.ABSENT) {
      String exists =
          """
          EXISTS (
            SELECT 1 FROM contact_identities ci
            WHERE ci.company_id = c.company_id
              AND ci.contact_id = c.id
              AND ci.status = 'ACTIVE'
              AND ci.identity_type = %s
          )
          """
              .formatted(param(state, identityType));
      return operator == SegmentOperator.PRESENT ? exists : "NOT (" + exists + ")";
    }
    String comparison =
        compileScalar("ci.normalized_value", ValueKind.TEXT, operator, predicate.value(), state);
    String exists =
        """
        EXISTS (
          SELECT 1 FROM contact_identities ci
          WHERE ci.company_id = c.company_id
            AND ci.contact_id = c.id
            AND ci.status = 'ACTIVE'
            AND ci.identity_type = %s
            AND %s
        )
        """
            .formatted(param(state, identityType), comparison);
    if (operator == SegmentOperator.CONTAINS) state.raise(CostClass.HIGH);
    return exists;
  }

  private String compileTag(PredicateNode predicate, CompileState state) {
    if (predicate.operator() != SegmentOperator.HAS_TAG
        || predicate.field().id() == null
        || !(predicate.value() == null || predicate.value() instanceof NoValue)) {
      throw invalid();
    }
    return """
        EXISTS (
          SELECT 1 FROM contact_tag_memberships ctm
          WHERE ctm.company_id = c.company_id
            AND ctm.contact_id = c.id
            AND ctm.tag_id = %s
        )
        """
        .formatted(param(state, predicate.field().id()));
  }

  private String compileList(PredicateNode predicate, CompileState state) {
    if (predicate.operator() != SegmentOperator.IN_LIST
        || predicate.field().id() == null
        || !(predicate.value() == null || predicate.value() instanceof NoValue)) {
      throw invalid();
    }
    return """
        EXISTS (
          SELECT 1 FROM contact_list_memberships clm
          WHERE clm.company_id = c.company_id
            AND clm.contact_id = c.id
            AND clm.list_id = %s
        )
        """
        .formatted(param(state, predicate.field().id()));
  }

  private String compileCustomField(PredicateNode predicate, CompileState state) {
    if (predicate.field().id() == null) throw invalid();
    var metadata =
        fieldMetadataRepository.requireActiveCustomField(state.companyId, predicate.field().id());
    String comparison;
    if (metadata.type() == CustomFieldType.MULTI_SELECT) {
      comparison =
          compileMultiSelect(
              "ccfv.text_array_value", predicate.operator(), predicate.value(), state);
    } else {
      CustomSql custom = customSql(metadata.type());
      comparison =
          compileScalar(
              custom.valueExpression(),
              custom.kind(),
              predicate.operator(),
              predicate.value(),
              state);
    }
    return """
        EXISTS (
          SELECT 1 FROM contact_custom_field_values ccfv
          WHERE ccfv.company_id = c.company_id
            AND ccfv.contact_id = c.id
            AND ccfv.field_id = %s
            AND %s
        )
        """
        .formatted(param(state, metadata.id()), comparison);
  }

  private String compileMultiSelect(
      String sql, SegmentOperator operator, SegmentValue value, CompileState state) {
    if (operator == SegmentOperator.PRESENT) {
      requireNoValue(value);
      return sql + " IS NOT NULL";
    }
    if (operator == SegmentOperator.ABSENT) {
      requireNoValue(value);
      return sql + " IS NULL";
    }
    if (operator == SegmentOperator.EQ || operator == SegmentOperator.NE) {
      String expression = sql + " @> ARRAY[" + param(state, requireText(value)) + "]::text[]";
      return operator == SegmentOperator.EQ ? expression : "NOT (" + expression + ")";
    }
    if (operator == SegmentOperator.IN || operator == SegmentOperator.NOT_IN) {
      if (!(value instanceof TextListValue texts)
          || texts.value() == null
          || texts.value().isEmpty()
          || texts.value().size() > properties.getSegmentMaxListValues()
          || texts.value().stream().anyMatch(Objects::isNull)) {
        throw invalid();
      }
      List<String> placeholders = new ArrayList<>(texts.value().size());
      for (String item : texts.value()) {
        placeholders.add(param(state, item));
      }
      String expression = sql + " && ARRAY[" + String.join(", ", placeholders) + "]::text[]";
      return operator == SegmentOperator.IN ? expression : "NOT (" + expression + ")";
    }
    throw invalid();
  }

  private String compilePreference(PredicateNode predicate, CompileState state) {
    String channel = requireChannel(predicate.field().key());
    String desired =
        switch (predicate.operator()) {
          case IS_OPTED_IN -> "OPTED_IN";
          case IS_OPTED_OUT -> "OPTED_OUT";
          default -> throw invalid();
        };
    requireNoValue(predicate.value());
    return """
        EXISTS (
          SELECT 1 FROM contact_channel_preferences ccp
          WHERE ccp.company_id = c.company_id
            AND ccp.contact_id = c.id
            AND ccp.channel = %s
            AND ccp.purpose = 'MARKETING'
            AND ccp.state = %s
        )
        """
        .formatted(param(state, channel), param(state, desired));
  }

  private String compileSuppression(PredicateNode predicate, CompileState state) {
    if (predicate.operator() != SegmentOperator.IS_SUPPRESSED) throw invalid();
    requireNoValue(predicate.value());
    String key = normalizedKey(predicate.field().key());
    if ("ANY".equals(key)) {
      return """
          EXISTS (
            SELECT 1 FROM contact_suppressions cs
            WHERE cs.company_id = c.company_id
              AND cs.contact_id = c.id
              AND cs.state = 'ACTIVE'
          )
          """;
    }
    String channel = requireChannel(key);
    return """
        EXISTS (
          SELECT 1 FROM contact_suppressions cs
          WHERE cs.company_id = c.company_id
            AND cs.contact_id = c.id
            AND cs.channel = %s
            AND cs.state = 'ACTIVE'
        )
        """
        .formatted(param(state, channel));
  }

  private String compileScalar(
      String sql,
      ValueKind kind,
      SegmentOperator operator,
      SegmentValue value,
      CompileState state) {
    if (operator == SegmentOperator.PRESENT) {
      requireNoValue(value);
      return sql + " IS NOT NULL";
    }
    if (operator == SegmentOperator.ABSENT) {
      requireNoValue(value);
      return sql + " IS NULL";
    }
    return switch (operator) {
      case EQ -> sql + " = " + param(state, scalar(kind, value));
      case NE -> sql + " <> " + param(state, scalar(kind, value));
      case GT -> numericOrTemporal(kind, sql, ">", value, state);
      case GTE -> numericOrTemporal(kind, sql, ">=", value, state);
      case LT -> numericOrTemporal(kind, sql, "<", value, state);
      case LTE -> numericOrTemporal(kind, sql, "<=", value, state);
      case BEFORE -> temporal(kind, sql, "<", value, state);
      case AFTER -> temporal(kind, sql, ">", value, state);
      case CONTAINS -> {
        if (kind != ValueKind.TEXT) throw invalid();
        String text = requireText(value);
        if (text.length() < 2) throw invalid();
        state.raise(CostClass.HIGH);
        yield "lower("
            + sql
            + ") like "
            + param(state, "%" + escapeLike(text.toLowerCase(Locale.ROOT)) + "%")
            + " escape '\\'";
      }
      case IN, NOT_IN -> compileIn(sql, kind, operator, value, state);
      default -> throw invalid();
    };
  }

  private String compileIn(
      String sql,
      ValueKind kind,
      SegmentOperator operator,
      SegmentValue value,
      CompileState state) {
    List<?> values = list(kind, value);
    if (values.isEmpty() || values.size() > properties.getSegmentMaxListValues()) throw invalid();
    List<String> placeholders = new ArrayList<>(values.size());
    for (Object item : values) placeholders.add(param(state, item));
    return sql
        + (operator == SegmentOperator.IN ? " IN (" : " NOT IN (")
        + String.join(", ", placeholders)
        + ")";
  }

  private String numericOrTemporal(
      ValueKind kind, String sql, String op, SegmentValue value, CompileState state) {
    if (kind != ValueKind.NUMBER && kind != ValueKind.DATE && kind != ValueKind.DATETIME) {
      throw invalid();
    }
    return sql + " " + op + " " + param(state, scalar(kind, value));
  }

  private String temporal(
      ValueKind kind, String sql, String op, SegmentValue value, CompileState state) {
    if (kind != ValueKind.DATE && kind != ValueKind.DATETIME) throw invalid();
    return sql + " " + op + " " + param(state, scalar(kind, value));
  }

  private Object scalar(ValueKind kind, SegmentValue value) {
    return switch (kind) {
      case TEXT -> requireText(value);
      case NUMBER -> {
        if (!(value instanceof NumberValue number) || number.value() == null) throw invalid();
        yield number.value();
      }
      case BOOLEAN -> {
        if (!(value instanceof BooleanValue bool) || bool.value() == null) throw invalid();
        yield bool.value();
      }
      case DATE -> {
        if (!(value instanceof DateValue date) || date.value() == null) throw invalid();
        yield date.value();
      }
      case DATETIME -> {
        if (!(value instanceof InstantValue instant) || instant.value() == null) throw invalid();
        yield instant.value();
      }
    };
  }

  private List<?> list(ValueKind kind, SegmentValue value) {
    if (kind == ValueKind.TEXT && value instanceof TextListValue texts && texts.value() != null) {
      if (texts.value().stream().anyMatch(Objects::isNull)) throw invalid();
      return texts.value();
    }
    if (value instanceof IdListValue ids && ids.value() != null && kind == ValueKind.NUMBER) {
      return ids.value();
    }
    throw invalid();
  }

  private String requireText(SegmentValue value) {
    if (!(value instanceof TextValue text) || text.value() == null) throw invalid();
    return text.value();
  }

  private void requireNoValue(SegmentValue value) {
    if (value != null && !(value instanceof NoValue)) throw invalid();
  }

  private String requireChannel(String key) {
    String channel = normalizedKey(key);
    if (!Set.of("EMAIL", "SMS", "WHATSAPP").contains(channel)) throw invalid();
    return channel;
  }

  private String normalizedKey(String key) {
    if (key == null || key.isBlank() || key.length() > 64) throw invalid();
    return key.trim().toUpperCase(Locale.ROOT);
  }

  private String param(CompileState state, Object value) {
    String key = "p" + state.nextParameter++;
    state.params.put(key, value);
    return ":" + key;
  }

  private CustomSql customSql(CustomFieldType type) {
    return switch (type) {
      case TEXT, SINGLE_SELECT -> new CustomSql("ccfv.text_value", ValueKind.TEXT);
      case NUMBER -> new CustomSql("ccfv.number_value", ValueKind.NUMBER);
      case BOOLEAN -> new CustomSql("ccfv.boolean_value", ValueKind.BOOLEAN);
      case DATE -> new CustomSql("ccfv.date_value", ValueKind.DATE);
      case DATETIME -> new CustomSql("ccfv.datetime_value", ValueKind.DATETIME);
      case MULTI_SELECT ->
          throw new BadRequestException(
              "CONTACT_SEGMENT_INVALID"); // Round-1 list-array operators are intentionally
        // deferred.
    };
  }

  private String hash(SegmentFilter filter) {
    try {
      byte[] bytes = objectMapper.writeValueAsString(filter).getBytes(StandardCharsets.UTF_8);
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
      return HexFormat.of().formatHex(digest);
    } catch (Exception exception) {
      throw new IllegalStateException("Unable to hash segment filter", exception);
    }
  }

  private String escapeLike(String value) {
    return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
  }

  private BadRequestException invalid() {
    return new BadRequestException("CONTACT_SEGMENT_INVALID");
  }

  private enum ValueKind {
    TEXT,
    NUMBER,
    BOOLEAN,
    DATE,
    DATETIME
  }

  private record BuiltinField(String sql, ValueKind kind) {}

  private record CustomSql(String valueExpression, ValueKind kind) {}

  public enum CostClass {
    LOW,
    MEDIUM,
    HIGH
  }

  public record CompiledSegment(
      String whereSql,
      Map<String, Object> parameters,
      CostClass costClass,
      String filterHash,
      int predicateCount) {}

  private static final class CompileState {
    private final long companyId;
    private final LinkedHashMap<String, Object> params = new LinkedHashMap<>();
    private int predicateCount;
    private int nextParameter;
    private CostClass cost = CostClass.LOW;

    private CompileState(long companyId) {
      this.companyId = companyId;
    }

    private void raise(CostClass candidate) {
      if (candidate.ordinal() > cost.ordinal()) cost = candidate;
    }
  }
}
