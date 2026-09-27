package com.messaging.ads.google.dto;

import java.math.BigDecimal;
import java.util.List;

public record GoogleCampaignSimulationResponse(
    String resourceName,
    String campaignId,
    String type,
    String modificationMethod,
    String startDate,
    String endDate,
    List<Point> points) {

  public GoogleCampaignSimulationResponse {
    points = points == null ? List.of() : List.copyOf(points);
  }

  public record Point(
      Long keyMicros,
      BigDecimal keyRatio,
      long impressions,
      long clicks,
      long costMicros,
      BigDecimal conversions,
      BigDecimal conversionValue,
      Long requiredBudgetMicros,
      Long requiredCpcBidCeilingMicros) {}
}
