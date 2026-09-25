package com.messaging.ads.meta;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.ads.dto.AdAccountHealthResponse;
import com.messaging.ads.entity.AdAccountHealth;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.meta.MetaAdsApiClient.AdAccountHealthSummary;
import com.messaging.ads.repository.AdAccountHealthRepository;
import java.time.Instant;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MetaAdsHealthPersistenceService {

  private final AdAccountHealthRepository healthRepository;
  private final ObjectMapper objectMapper;

  @Transactional
  public AdAccountHealthResponse persist(
      Long companyId,
      Long accountId,
      AdAccountHealthSummary summary) {
    AdAccountHealth health =
        healthRepository
            .findByAdAccountIdAndProvider(accountId, AdProvider.META_ADS)
            .orElseGet(AdAccountHealth::new);

    if (health.getId() == null) {
      health.setCompanyId(companyId);
      health.setAdAccountId(accountId);
      health.setProvider(AdProvider.META_ADS);
    }

    health.setAccountStatus(summary.accountStatus());
    health.setDisableReason(summary.disableReason());
    health.setOpportunityScore(summary.opportunityScore());
    health.setOpportunityScoreWeight(summary.opportunityScoreWeight());
    health.setPageAuthorized(summary.pageAuthorized());
    health.setOffsitePixelsTosAccepted(summary.offsitePixelsTosAccepted());
    health.setDirectDealsEnabled(summary.directDealsEnabled());
    health.setNotificationsEnabled(summary.notificationsEnabled());
    health.setAccessExpiresAt(parseInstant(summary.userAccessExpireTime()));
    health.setOffsiteCloSignalStatus(summary.offsiteCloSignalStatus());
    health.setCapabilitiesJson(json(summary.capabilities()));
    health.setAllCapabilitiesJson(json(summary.allCapabilities()));
    health.setUserTasksJson(json(summary.userTasks()));
    health.setFailedDeliveryChecksJson(json(summary.failedDeliveryChecks()));
    health.setProviderPayloadJson(json(summary));
    health.setLastSyncAt(Instant.now());

    return AdAccountHealthResponse.from(healthRepository.save(health), objectMapper);
  }

  private Instant parseInstant(String value) {
    if (value == null || value.isBlank()) return null;
    try {
      return Instant.parse(value.trim());
    } catch (RuntimeException ignored) {
      try {
        return OffsetDateTime.parse(value.trim()).toInstant();
      } catch (RuntimeException ignoredAgain) {
        return null;
      }
    }
  }

  private String json(Object value) {
    if (value == null) return null;
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException(
          "Unable to serialize Meta account health provider data", exception);
    }
  }
}
