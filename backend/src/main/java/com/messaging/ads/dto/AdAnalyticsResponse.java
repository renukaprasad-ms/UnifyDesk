package com.messaging.ads.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record AdAnalyticsResponse(
    Summary summary,
    List<DailyPoint> daily,
    List<EntityRow> entities) {

  public record Summary(
      String currency,
      BigDecimal spend,
      long impressions,
      long reach,
      long clicks,
      BigDecimal ctr,
      BigDecimal cpc,
      BigDecimal cpm,
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
      long reach,
      long clicks,
      BigDecimal conversions,
      BigDecimal conversionValue,
      BigDecimal roas) {}

  public record EntityRow(
      String entityRef,
      String entityName,
      BigDecimal spend,
      long impressions,
      long reach,
      long clicks,
      BigDecimal ctr,
      BigDecimal conversions,
      BigDecimal conversionValue,
      BigDecimal cpa,
      BigDecimal roas,
      BigDecimal leads,
      BigDecimal purchases) {}
}
