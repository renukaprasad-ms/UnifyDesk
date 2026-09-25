package com.messaging.ads.google.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record GoogleAiMaxCombinationReportResponse(
    String campaignId,
    LocalDate from,
    LocalDate to,
    String currency,
    List<Row> rows) {

  public record Row(
      String searchTerm,
      String headline,
      String landingPage,
      String externalAdGroupId,
      String adGroupName,
      long impressions,
      long clicks,
      BigDecimal spend,
      BigDecimal conversions,
      BigDecimal conversionValue,
      BigDecimal cpa,
      BigDecimal roas) {}
}
