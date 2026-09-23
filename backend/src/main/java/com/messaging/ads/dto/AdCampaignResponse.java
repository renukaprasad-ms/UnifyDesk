package com.messaging.ads.dto;

import com.messaging.ads.entity.AdCampaign;
import com.messaging.ads.enums.AdEntityStatus;
import com.messaging.ads.enums.AdProvider;
import java.time.Instant;

public record AdCampaignResponse(
    String id,
    String companyId,
    String adAccountId,
    AdProvider provider,
    String externalCampaignId,
    String name,
    String objective,
    String buyingType,
    String bidStrategy,
    AdEntityStatus status,
    String providerStatus,
    String effectiveStatus,
    Long dailyBudgetMinor,
    Long lifetimeBudgetMinor,
    Long spendCapMinor,
    Instant startAt,
    Instant endAt,
    Instant providerCreatedAt,
    Instant providerUpdatedAt,
    boolean accessible,
    Instant lastSyncAt) {

  public static AdCampaignResponse from(AdCampaign campaign) {
    return new AdCampaignResponse(
        campaign.getId().toString(),
        campaign.getCompanyId().toString(),
        campaign.getAdAccountId().toString(),
        campaign.getProvider(),
        campaign.getExternalCampaignId(),
        campaign.getName(),
        campaign.getObjective(),
        campaign.getBuyingType(),
        campaign.getBidStrategy(),
        campaign.getStatus(),
        campaign.getProviderStatus(),
        campaign.getEffectiveStatus(),
        campaign.getDailyBudgetMinor(),
        campaign.getLifetimeBudgetMinor(),
        campaign.getSpendCapMinor(),
        campaign.getStartAt(),
        campaign.getEndAt(),
        campaign.getProviderCreatedAt(),
        campaign.getProviderUpdatedAt(),
        campaign.isAccessible(),
        campaign.getLastSyncAt());
  }
}
