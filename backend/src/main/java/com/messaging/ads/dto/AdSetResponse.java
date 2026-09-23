package com.messaging.ads.dto;

import com.messaging.ads.entity.AdSet;
import com.messaging.ads.enums.AdEntityStatus;
import com.messaging.ads.enums.AdProvider;
import java.time.Instant;

public record AdSetResponse(
    String id,
    String companyId,
    String adAccountId,
    String campaignId,
    AdProvider provider,
    String externalAdSetId,
    String name,
    String optimizationGoal,
    String billingEvent,
    String bidStrategy,
    String destinationType,
    AdEntityStatus status,
    String providerStatus,
    String effectiveStatus,
    Long dailyBudgetMinor,
    Long lifetimeBudgetMinor,
    Long bidAmountMinor,
    Instant startAt,
    Instant endAt,
    String targetingJson,
    String promotedObjectJson,
    String attributionSpecJson,
    Instant providerCreatedAt,
    Instant providerUpdatedAt,
    boolean accessible,
    Instant lastSyncAt) {

  public static AdSetResponse from(AdSet adSet) {
    return new AdSetResponse(
        adSet.getId().toString(),
        adSet.getCompanyId().toString(),
        adSet.getAdAccountId().toString(),
        adSet.getCampaignId().toString(),
        adSet.getProvider(),
        adSet.getExternalAdSetId(),
        adSet.getName(),
        adSet.getOptimizationGoal(),
        adSet.getBillingEvent(),
        adSet.getBidStrategy(),
        adSet.getDestinationType(),
        adSet.getStatus(),
        adSet.getProviderStatus(),
        adSet.getEffectiveStatus(),
        adSet.getDailyBudgetMinor(),
        adSet.getLifetimeBudgetMinor(),
        adSet.getBidAmountMinor(),
        adSet.getStartAt(),
        adSet.getEndAt(),
        adSet.getTargetingJson(),
        adSet.getPromotedObjectJson(),
        adSet.getAttributionSpecJson(),
        adSet.getProviderCreatedAt(),
        adSet.getProviderUpdatedAt(),
        adSet.isAccessible(),
        adSet.getLastSyncAt());
  }
}
