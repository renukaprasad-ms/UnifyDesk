package com.messaging.ads.google.dto;

import java.math.BigDecimal;
import java.util.List;

public record GoogleExperimentResponse(
    String id,
    String resourceName,
    String name,
    String description,
    String type,
    String status,
    String suffix,
    String startDate,
    String endDate,
    String promoteStatus,
    String longRunningOperation,
    List<Arm> arms,
    Metrics metrics) {

  public GoogleExperimentResponse {
    arms = arms == null ? List.of() : List.copyOf(arms);
  }

  public record Arm(
      String resourceName,
      String name,
      boolean control,
      long trafficSplit,
      List<String> campaigns,
      List<String> inDesignCampaigns) {
    public Arm {
      campaigns = campaigns == null ? List.of() : List.copyOf(campaigns);
      inDesignCampaigns =
          inDesignCampaigns == null ? List.of() : List.copyOf(inDesignCampaigns);
    }
  }

  public record Metrics(
      long impressions,
      long controlImpressions,
      long clicks,
      long controlClicks,
      long costMicros,
      long controlCostMicros,
      BigDecimal conversions,
      BigDecimal controlConversions,
      BigDecimal conversionValue,
      BigDecimal controlConversionValue,
      BigDecimal clicksPointEstimate,
      BigDecimal clicksMarginOfError,
      BigDecimal clicksPValue,
      BigDecimal conversionsAbsoluteChangePointEstimate,
      BigDecimal conversionsAbsoluteChangeMarginOfError,
      BigDecimal conversionsAbsoluteChangePValue) {}
}
