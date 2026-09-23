package com.messaging.ads.dto;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.ads.entity.AdAccount;
import com.messaging.ads.entity.AdAccountHealth;
import com.messaging.ads.enums.AdProvider;
import java.time.Instant;
import java.util.List;

public record AdAccountHealthResponse(
    boolean synced,
    String id,
    String companyId,
    String adAccountId,
    AdProvider provider,
    Integer accountStatus,
    Integer disableReason,
    java.math.BigDecimal opportunityScore,
    Integer opportunityScoreWeight,
    Boolean pageAuthorized,
    Boolean offsitePixelsTosAccepted,
    Boolean directDealsEnabled,
    Boolean notificationsEnabled,
    Instant accessExpiresAt,
    Integer offsiteCloSignalStatus,
    List<String> capabilities,
    List<String> allCapabilities,
    List<String> userTasks,
    JsonNode failedDeliveryChecks,
    Instant lastSyncAt) {

  public static AdAccountHealthResponse from(
      AdAccountHealth health, ObjectMapper objectMapper) {
    return new AdAccountHealthResponse(
        true,
        health.getId().toString(),
        health.getCompanyId().toString(),
        health.getAdAccountId().toString(),
        health.getProvider(),
        health.getAccountStatus(),
        health.getDisableReason(),
        health.getOpportunityScore(),
        health.getOpportunityScoreWeight(),
        health.getPageAuthorized(),
        health.getOffsitePixelsTosAccepted(),
        health.getDirectDealsEnabled(),
        health.getNotificationsEnabled(),
        health.getAccessExpiresAt(),
        health.getOffsiteCloSignalStatus(),
        strings(objectMapper, health.getCapabilitiesJson()),
        strings(objectMapper, health.getAllCapabilitiesJson()),
        strings(objectMapper, health.getUserTasksJson()),
        tree(objectMapper, health.getFailedDeliveryChecksJson()),
        health.getLastSyncAt());
  }

  public static AdAccountHealthResponse empty(AdAccount account) {
    return new AdAccountHealthResponse(
        false,
        null,
        account.getCompanyId().toString(),
        account.getId().toString(),
        account.getProvider(),
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        List.of(),
        List.of(),
        List.of(),
        null,
        null);
  }

  private static List<String> strings(ObjectMapper objectMapper, String value) {
    if (value == null || value.isBlank()) return List.of();
    try {
      return objectMapper.readValue(value, new TypeReference<List<String>>() {});
    } catch (Exception ignored) {
      return List.of();
    }
  }

  private static JsonNode tree(ObjectMapper objectMapper, String value) {
    if (value == null || value.isBlank()) return null;
    try {
      return objectMapper.readTree(value);
    } catch (Exception ignored) {
      return null;
    }
  }
}
