package com.messaging.ads.google.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record GoogleKeywordForecastResponse(
    boolean cached,
    String currency,
    LocalDate startDate,
    LocalDate endDate,
    String biddingStrategy,
    int keywordCount,
    List<String> geoTargetResources,
    String languageResource,
    Double clicks,
    BigDecimal cost,
    BigDecimal averageCpc,
    Double conversions,
    BigDecimal averageCpa) {

  public GoogleKeywordForecastResponse {
    geoTargetResources =
        geoTargetResources == null ? List.of() : List.copyOf(geoTargetResources);
  }

  public GoogleKeywordForecastResponse asCached() {
    return new GoogleKeywordForecastResponse(
        true,
        currency,
        startDate,
        endDate,
        biddingStrategy,
        keywordCount,
        geoTargetResources,
        languageResource,
        clicks,
        cost,
        averageCpc,
        conversions,
        averageCpa);
  }
}
