package com.messaging.ads.campaign.dto;

import com.messaging.ads.campaign.enums.CampaignChannelType;
import com.messaging.ads.enums.AdProvider;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ReachlyoBudgetRecommendationResponse(
    String campaignId,
    String currency,
    LocalDate from,
    LocalDate to,
    String confidence,
    boolean actionable,
    String methodology,
    List<ChannelRecommendation> channels) {

  public ReachlyoBudgetRecommendationResponse {
    channels = channels == null ? List.of() : List.copyOf(channels);
  }

  public record ChannelRecommendation(
      String channelId,
      AdProvider provider,
      CampaignChannelType channelType,
      BigDecimal currentPlannedPercent,
      BigDecimal historicalSpendSharePercent,
      BigDecimal recommendedPercent,
      Long recommendedBudgetMinor,
      BigDecimal spend,
      BigDecimal conversions,
      BigDecimal cpa,
      BigDecimal roas,
      String reason) {}
}
