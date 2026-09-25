package com.messaging.ads.google.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record GoogleSearchTermReportResponse(
    String campaignId,
    LocalDate from,
    LocalDate to,
    String currency,
    List<Row> rows) {

  public record Row(
      String searchTerm,
      String targetingStatus,
      String matchedKeyword,
      String matchedKeywordMatchType,
      String searchTermMatchType,
      String searchTermMatchSource,
      String externalAdGroupId,
      String adGroupId,
      String adGroupName,
      long impressions,
      long clicks,
      BigDecimal ctr,
      BigDecimal spend,
      BigDecimal conversions,
      BigDecimal conversionValue,
      BigDecimal cpa,
      BigDecimal roas) {}
}
