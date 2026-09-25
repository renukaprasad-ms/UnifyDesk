package com.messaging.ads.campaign.dto;

import com.messaging.ads.campaign.enums.CampaignChannelType;
import com.messaging.ads.campaign.enums.ProviderDeploymentStatus;
import com.messaging.ads.enums.AdProvider;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record ReachlyoCampaignAnalyticsResponse(
    String campaignId,
    String currency,
    LocalDate from,
    LocalDate to,
    Summary summary,
    List<DailyPoint> daily,
    List<DeploymentRow> deployments,
    boolean exactRangeAvailable,
    Instant lastSyncedAt) {

  public record Summary(
      BigDecimal spend,
      long impressions,
      long clicks,
      BigDecimal ctr,
      BigDecimal conversions,
      BigDecimal conversionValue,
      BigDecimal cpa,
      BigDecimal roas,
      BigDecimal leads,
      BigDecimal purchases) {}

  public record DailyPoint(
      LocalDate date,
      BigDecimal spend,
      long impressions,
      long clicks,
      BigDecimal conversions,
      BigDecimal conversionValue) {}

  public record DeploymentRow(
      String deploymentId,
      String channelId,
      AdProvider provider,
      CampaignChannelType channelType,
      String adAccountId,
      String localCampaignId,
      String externalCampaignId,
      String providerStatus,
      ProviderDeploymentStatus deploymentStatus,
      Summary summary,
      BigDecimal spendSharePercent,
      boolean exactRangeAvailable,
      Instant lastSyncedAt) {}
}
