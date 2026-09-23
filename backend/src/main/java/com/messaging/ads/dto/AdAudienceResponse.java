package com.messaging.ads.dto;

import com.messaging.ads.entity.AdAudience;
import com.messaging.ads.enums.AdProvider;
import java.time.Instant;

public record AdAudienceResponse(
    String id, String companyId, String adAccountId, AdProvider provider,
    String externalAudienceId, String name, String subtype, String description,
    Integer retentionDays, Long approximateCountLowerBound, Long approximateCountUpperBound,
    String deliveryStatusJson, String operationStatusJson, String pixelId, String ruleJson,
    String lookalikeSpecJson, Instant providerCreatedAt, Instant providerUpdatedAt,
    boolean accessible, Instant lastSyncAt) {
  public static AdAudienceResponse from(AdAudience value) {
    return new AdAudienceResponse(
        value.getId().toString(), value.getCompanyId().toString(), value.getAdAccountId().toString(),
        value.getProvider(), value.getExternalAudienceId(), value.getName(), value.getSubtype(),
        value.getDescription(), value.getRetentionDays(), value.getApproximateCountLowerBound(),
        value.getApproximateCountUpperBound(), value.getDeliveryStatusJson(),
        value.getOperationStatusJson(), value.getPixelId(), value.getRuleJson(),
        value.getLookalikeSpecJson(), value.getProviderCreatedAt(), value.getProviderUpdatedAt(),
        value.isAccessible(), value.getLastSyncAt());
  }
}
