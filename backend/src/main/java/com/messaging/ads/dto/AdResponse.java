package com.messaging.ads.dto;

import com.messaging.ads.entity.Ad;
import com.messaging.ads.enums.AdEntityStatus;
import com.messaging.ads.enums.AdProvider;
import java.time.Instant;

public record AdResponse(
    String id,
    String companyId,
    String adAccountId,
    String campaignId,
    String adSetId,
    String creativeId,
    AdProvider provider,
    String externalAdId,
    String externalCreativeId,
    String name,
    AdEntityStatus status,
    String providerStatus,
    String effectiveStatus,
    String trackingSpecsJson,
    String conversionSpecsJson,
    Instant providerCreatedAt,
    Instant providerUpdatedAt,
    boolean accessible,
    Instant lastSyncAt) {

  public static AdResponse from(Ad ad) {
    return new AdResponse(
        ad.getId().toString(),
        ad.getCompanyId().toString(),
        ad.getAdAccountId().toString(),
        ad.getCampaignId().toString(),
        ad.getAdSetId().toString(),
        ad.getCreativeId() == null ? null : ad.getCreativeId().toString(),
        ad.getProvider(),
        ad.getExternalAdId(),
        ad.getExternalCreativeId(),
        ad.getName(),
        ad.getStatus(),
        ad.getProviderStatus(),
        ad.getEffectiveStatus(),
        ad.getTrackingSpecsJson(),
        ad.getConversionSpecsJson(),
        ad.getProviderCreatedAt(),
        ad.getProviderUpdatedAt(),
        ad.isAccessible(),
        ad.getLastSyncAt());
  }
}
