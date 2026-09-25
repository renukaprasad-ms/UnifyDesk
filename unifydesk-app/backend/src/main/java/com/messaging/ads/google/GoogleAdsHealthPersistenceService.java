package com.messaging.ads.google;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.ads.dto.AdAccountHealthResponse;
import com.messaging.ads.entity.AdAccountHealth;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.repository.AdAccountHealthRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GoogleAdsHealthPersistenceService {

  private final AdAccountHealthRepository repository;
  private final ObjectMapper objectMapper;

  @Transactional
  public AdAccountHealthResponse persist(
      Long companyId, Long accountId, JsonNode row) {
    JsonNode customer = row.path("customer");
    AdAccountHealth health =
        repository
            .findByAdAccountIdAndProvider(accountId, AdProvider.GOOGLE_ADS)
            .orElseGet(AdAccountHealth::new);

    if (health.getId() == null) {
      health.setCompanyId(companyId);
      health.setAdAccountId(accountId);
      health.setProvider(AdProvider.GOOGLE_ADS);
    }

    health.setAccountStatus(statusCode(text(customer, "status")));
    health.setDisableReason(
        "SUSPENDED".equalsIgnoreCase(text(customer, "status")) ? 1 : null);
    health.setOpportunityScore(decimal(customer, "optimizationScore"));
    health.setOpportunityScoreWeight(null);
    health.setPageAuthorized(null);
    health.setOffsitePixelsTosAccepted(null);
    health.setDirectDealsEnabled(null);
    health.setNotificationsEnabled(null);
    health.setAccessExpiresAt(null);
    health.setOffsiteCloSignalStatus(null);

    List<String> capabilities = new ArrayList<>();
    if (customer.path("manager").asBoolean(false)) capabilities.add("MANAGER_ACCOUNT");
    if (customer.path("testAccount").asBoolean(false)) capabilities.add("TEST_ACCOUNT");
    if (customer.path("autoTaggingEnabled").asBoolean(false)) capabilities.add("AUTO_TAGGING");
    String conversionStatus =
        text(customer.path("conversionTrackingSetting"), "conversionTrackingStatus");
    if (conversionStatus != null) {
      capabilities.add("CONVERSION_TRACKING_" + conversionStatus);
    }
    health.setCapabilitiesJson(json(capabilities));
    health.setAllCapabilitiesJson(json(capabilities));
    health.setUserTasksJson(json(List.of()));

    List<Map<String, Object>> checks = new ArrayList<>();
    JsonNode reasons = customer.path("payPerConversionEligibilityFailureReasons");
    if (reasons.isArray()) {
      for (JsonNode reason : reasons) {
        String value = reason.asText(null);
        if (value == null || value.isBlank()) continue;
        checks.add(
            Map.of(
                "check_name", "PAY_PER_CONVERSION_" + value,
                "summary", "Google pay-per-conversion eligibility",
                "description", value));
      }
    }
    health.setFailedDeliveryChecksJson(json(checks));
    health.setProviderPayloadJson(json(row));
    health.setLastSyncAt(Instant.now());

    return AdAccountHealthResponse.from(repository.save(health), objectMapper);
  }

  private int statusCode(String status) {
    if (status == null) return 0;
    return switch (status.toUpperCase()) {
      case "ENABLED" -> 1;
      case "SUSPENDED" -> 2;
      case "CANCELED", "CLOSED" -> 3;
      default -> 0;
    };
  }

  private BigDecimal decimal(JsonNode node, String field) {
    String value = text(node, field);
    if (value == null) return null;
    try {
      return new BigDecimal(value);
    } catch (NumberFormatException ignored) {
      return null;
    }
  }

  private String text(JsonNode node, String field) {
    if (node == null || node.path(field).isMissingNode() || node.path(field).isNull()) return null;
    String value = node.path(field).asText(null);
    return value == null || value.isBlank() ? null : value.trim();
  }

  private String json(Object value) {
    if (value == null) return null;
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Unable to serialize Google Ads account health", exception);
    }
  }
}
