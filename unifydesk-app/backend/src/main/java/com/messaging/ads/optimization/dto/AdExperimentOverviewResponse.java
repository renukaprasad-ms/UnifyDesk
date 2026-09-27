package com.messaging.ads.optimization.dto;

import com.messaging.ads.enums.AdProvider;
import java.math.BigDecimal;
import java.util.List;

public record AdExperimentOverviewResponse(
    List<Experiment> experiments,
    List<ProviderFailure> failures) {

  public AdExperimentOverviewResponse {
    experiments = experiments == null ? List.of() : List.copyOf(experiments);
    failures = failures == null ? List.of() : List.copyOf(failures);
  }

  public record Experiment(
      AdProvider provider,
      String adAccountId,
      String providerExperimentId,
      String name,
      String type,
      String status,
      String managementMode,
      String startAt,
      String endAt,
      long impressions,
      long controlImpressions,
      long clicks,
      long controlClicks,
      long costMicros,
      long controlCostMicros,
      BigDecimal conversions,
      BigDecimal controlConversions,
      BigDecimal conversionValue,
      BigDecimal controlConversionValue) {}

  public record ProviderFailure(
      AdProvider provider,
      String adAccountId,
      String message) {}
}
