package com.messaging.contacts.audience;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import tools.jackson.databind.JsonNode;
import com.messaging.contacts.audience.SegmentFilter.FieldKind;
import com.messaging.contacts.audience.SegmentFilter.GroupOperator;
import com.messaging.contacts.audience.SegmentFilter.SegmentOperator;
import java.time.Instant;
import java.util.List;

public final class SegmentApiModel {

  private SegmentApiModel() {}

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes({
    @JsonSubTypes.Type(value = Group.class, name = "group"),
    @JsonSubTypes.Type(value = Predicate.class, name = "predicate")
  })
  public sealed interface Node permits Group, Predicate {}

  public record Group(GroupOperator op, List<Node> children) implements Node {}

  public record Predicate(Field field, SegmentOperator operator, JsonNode value) implements Node {}

  public record Field(FieldKind kind, String key, String fieldId) {}

  public record UpsertRequest(
      String name,
      String description,
      int filterVersion,
      Node filter,
      Long version) {}

  public record FilterRequest(int filterVersion, Node filter, Integer sampleSize) {}

  public record ValidationError(String path, String code, String message) {}

  public record ValidationResponse(
      boolean valid,
      Node normalizedFilter,
      String filterHash,
      String costClass,
      List<ValidationError> errors) {}

  public record ContactSample(
      String id,
      String displayName,
      String firstName,
      String lastName,
      String organization,
      String primaryEmail,
      String primaryPhone,
      String status,
      Instant createdAt,
      Instant updatedAt,
      Instant lastActivityAt) {}

  public record PreviewResponse(
      List<ContactSample> sample,
      Long count,
      String countState,
      Instant countComputedAt) {}

  public record SegmentResponse(
      String id,
      String name,
      String description,
      int filterVersion,
      Node filter,
      String filterHash,
      Long count,
      String countState,
      Instant countComputedAt,
      boolean archived,
      long version,
      Instant createdAt,
      Instant updatedAt) {}
}
