package com.messaging.contacts.audience;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record SegmentFilter(int version, SegmentNode root) {

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes({
    @JsonSubTypes.Type(value = GroupNode.class, name = "GROUP"),
    @JsonSubTypes.Type(value = PredicateNode.class, name = "PREDICATE")
  })
  public sealed interface SegmentNode permits GroupNode, PredicateNode {}

  public record GroupNode(GroupOperator op, List<SegmentNode> children) implements SegmentNode {}

  public record PredicateNode(SegmentField field, SegmentOperator operator, SegmentValue value)
      implements SegmentNode {}

  public record SegmentField(FieldKind kind, String key, Long id) {}

  public enum GroupOperator {
    AND,
    OR
  }

  public enum FieldKind {
    BUILTIN_CONTACT,
    IDENTITY,
    TAG,
    LIST,
    CUSTOM_FIELD,
    CHANNEL_PREFERENCE,
    SUPPRESSION,
    CREATED_AT,
    LAST_ACTIVITY_AT
  }

  public enum SegmentOperator {
    EQ,
    NE,
    IN,
    NOT_IN,
    PRESENT,
    ABSENT,
    CONTAINS,
    GT,
    GTE,
    LT,
    LTE,
    BEFORE,
    AFTER,
    HAS_TAG,
    IN_LIST,
    IS_OPTED_IN,
    IS_OPTED_OUT,
    IS_SUPPRESSED
  }

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes({
    @JsonSubTypes.Type(value = TextValue.class, name = "TEXT"),
    @JsonSubTypes.Type(value = NumberValue.class, name = "NUMBER"),
    @JsonSubTypes.Type(value = BooleanValue.class, name = "BOOLEAN"),
    @JsonSubTypes.Type(value = DateValue.class, name = "DATE"),
    @JsonSubTypes.Type(value = InstantValue.class, name = "DATETIME"),
    @JsonSubTypes.Type(value = IdValue.class, name = "ID"),
    @JsonSubTypes.Type(value = TextListValue.class, name = "TEXT_LIST"),
    @JsonSubTypes.Type(value = IdListValue.class, name = "ID_LIST"),
    @JsonSubTypes.Type(value = NoValue.class, name = "NONE")
  })
  public sealed interface SegmentValue
      permits TextValue,
          NumberValue,
          BooleanValue,
          DateValue,
          InstantValue,
          IdValue,
          TextListValue,
          IdListValue,
          NoValue {}

  public record TextValue(String value) implements SegmentValue {}

  public record NumberValue(BigDecimal value) implements SegmentValue {}

  public record BooleanValue(Boolean value) implements SegmentValue {}

  public record DateValue(LocalDate value) implements SegmentValue {}

  public record InstantValue(Instant value) implements SegmentValue {}

  public record IdValue(Long value) implements SegmentValue {}

  public record TextListValue(List<String> value) implements SegmentValue {}

  public record IdListValue(List<Long> value) implements SegmentValue {}

  public record NoValue() implements SegmentValue {}
}
