package com.messaging.ads.dto;

import com.messaging.ads.enums.AdInsightBreakdownType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record AdAnalyticsBreakdownResponse(
    AdInsightBreakdownType breakdown,
    List<Row> rows,
    Instant lastSyncedAt) {

  public record Row(
      String dimension,
      String secondaryDimension,
      String actionType,
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
      BigDecimal leads,
      BigDecimal purchases,
      BigDecimal actionValue) {}
}
