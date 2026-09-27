package com.messaging.managedsms.control.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.messaging.managedsms.control.enums.ManagedSmsEnums.*;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;

public final class ManagedSmsAdminDtos {
  private ManagedSmsAdminDtos() {}

  public record DecisionRequest(
      @NotNull AdminDecision decision,
      @NotBlank @Size(max = 500) String reason,
      @Min(0) long version,
      @Size(max = 255) String providerAccountRef,
      @Size(max = 255) String providerClientRef,
      @Size(max = 255) String providerTemplateRef,
      @Size(max = 255) String providerFlowRef,
      @Size(max = 255) String providerMappingRef,
      JsonNode ctaPolicy) {}

  public record TenantActionRequest(
      @NotBlank @Size(max = 500) String reason, @Min(0) long version) {}

  public record ReviewItem(
      String resourceType,
      String id,
      String companyId,
      String state,
      String summary,
      long version,
      Instant updatedAt) {}

  public record ReviewQueueResponse(List<ReviewItem> items) {}

  public record DecisionResponse(
      String resourceType, String id, String state, long version, Instant changedAt) {}
}
