package com.messaging.managedsms.control.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.messaging.broadcast.dto.BroadcastSenderResponse;
import com.messaging.managedsms.control.enums.ManagedSmsEnums.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class ManagedSmsDtos {
  private ManagedSmsDtos() {}

  public record DltProfileRequest(
      @NotBlank @Size(max = 255) String legalName,
      @NotNull DltPlatform dltPlatform,
      @NotBlank @Size(max = 160) String peId,
      @Size(max = 255) String tmChainReference,
      JsonNode evidence) {}

  public record DltProfilePatchRequest(
      @Size(max = 255) String legalName,
      DltPlatform dltPlatform,
      @Size(max = 160) String peId,
      @Size(max = 255) String tmChainReference,
      JsonNode evidence) {}

  public record HeaderRequest(
      @NotBlank @Size(max = 32) String header, @NotNull MessageCategory messageCategory) {}

  public record VariableDefinition(
      @NotBlank @Size(max = 80) String key,
      @NotNull VariableType type,
      @Min(1) @Max(500) int maxLength) {}

  public record TemplateRequest(
      @NotBlank @Size(max = 160) String name,
      @NotEmpty List<@NotBlank String> headerIds,
      @NotNull MessageCategory messageCategory,
      @NotBlank @Size(max = 255) String dltTemplateId,
      @NotBlank @Size(max = 4000) String bodyText,
      @NotNull List<@Valid VariableDefinition> variables,
      JsonNode ctaPolicy,
      JsonNode consentPolicy,
      JsonNode optOutPolicy) {}

  public record TemplateVersionRequest(
      @NotEmpty List<@NotBlank String> headerIds,
      @NotBlank @Size(max = 255) String dltTemplateId,
      @NotBlank @Size(max = 4000) String bodyText,
      @NotNull List<@Valid VariableDefinition> variables,
      JsonNode ctaPolicy,
      JsonNode consentPolicy,
      JsonNode optOutPolicy) {}

  public record PreviewRequest(
      @NotBlank String headerId, @NotNull Map<String, String> variables) {}

  public record CreateSenderRequest(
      @NotBlank String headerId, @Size(max = 255) String displayName) {}

  public record DltProfileResponse(
      String id,
      String legalName,
      DltPlatform dltPlatform,
      String peId,
      DltProfileStatus state,
      TmChainStatus chainState,
      String tmChainReference,
      Instant verifiedAt,
      Instant checkedAt,
      Instant staleAfter,
      long version) {}

  public record HeaderResponse(
      String id,
      String header,
      MessageCategory messageCategory,
      HeaderStatus state,
      String providerSyncState,
      String actionRequired,
      Instant updatedAt,
      long version) {}

  public record TemplateVersionResponse(
      String id,
      long version,
      String dltTemplateId,
      String bodyText,
      List<VariableDefinition> variables,
      List<String> allowedHeaderIds,
      TemplateVersionStatus state,
      String providerSyncState,
      Instant checkedAt,
      Instant staleAfter,
      long lockVersion) {}

  public record TemplateResponse(
      String id,
      String name,
      MessageCategory messageCategory,
      TemplateStatus state,
      TemplateVersionResponse activeVersion,
      TemplateVersionResponse latestVersion,
      Instant updatedAt) {}

  public record PreviewResponse(String body, List<String> errors) {}

  public record StatusDltProfile(
      String id, String peIdMasked, DltProfileStatus state, TmChainStatus chainState) {}

  public record UsageSummary(
      Instant periodStart, Instant periodEnd, long acceptedSegments, Long limit) {}

  public record StatusResponse(
      boolean enabled,
      CustomerState state,
      String nextAction,
      StatusDltProfile dltProfile,
      long readyHeaderCount,
      long readyTemplateCount,
      boolean sendingEnabled,
      UsageSummary usage) {}

  public record SenderListResponse(List<BroadcastSenderResponse> senders) {}
}
