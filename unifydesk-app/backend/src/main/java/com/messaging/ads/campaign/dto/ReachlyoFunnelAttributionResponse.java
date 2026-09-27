package com.messaging.ads.campaign.dto;

import com.messaging.ads.campaign.enums.CampaignChannelType;
import com.messaging.ads.enums.AdProvider;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ReachlyoFunnelAttributionResponse(
    String campaignId,
    String currency,
    LocalDate from,
    LocalDate to,
    String attributionMode,
    String limitation,
    Funnel totals,
    List<DeploymentFunnel> deployments) {

  public ReachlyoFunnelAttributionResponse {
    deployments = deployments == null ? List.of() : List.copyOf(deployments);
  }

  public record Funnel(
      long impressions,
      long clicks,
      BigDecimal leads,
      BigDecimal conversions,
      BigDecimal purchases,
      BigDecimal spend,
      BigDecimal conversionValue,
      BigDecimal clickThroughRate,
      BigDecimal clickToLeadRate,
      BigDecimal leadToPurchaseRate,
      BigDecimal clickToConversionRate,
      BigDecimal cpa,
      BigDecimal roas) {}

  public record DeploymentFunnel(
      String deploymentId,
      AdProvider provider,
      CampaignChannelType channelType,
      String localCampaignId,
      Funnel funnel) {}
}
