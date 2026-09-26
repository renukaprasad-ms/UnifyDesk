package com.messaging.ads.google.dto;

import java.math.BigDecimal;
import java.util.List;

public record GoogleProviderReportResponse(
    List<LandingPageRow> landingPages,
    List<PlacementRow> placements,
    List<AuctionInsightRow> auctionInsights,
    List<ConversionAttributionRow> conversionAttribution,
    List<ChangeEventRow> changeEvents) {

  public GoogleProviderReportResponse {
    landingPages = landingPages == null ? List.of() : List.copyOf(landingPages);
    placements = placements == null ? List.of() : List.copyOf(placements);
    auctionInsights = auctionInsights == null ? List.of() : List.copyOf(auctionInsights);
    conversionAttribution =
        conversionAttribution == null ? List.of() : List.copyOf(conversionAttribution);
    changeEvents = changeEvents == null ? List.of() : List.copyOf(changeEvents);
  }

  public record LandingPageRow(
      String campaignId,
      String campaignName,
      String adGroupId,
      String adGroupName,
      String url,
      long impressions,
      long clicks,
      long costMicros,
      BigDecimal conversions,
      BigDecimal conversionValue) {}

  public record PlacementRow(
      String campaignId,
      String campaignName,
      String adGroupId,
      String adGroupName,
      String placement,
      String displayName,
      String placementType,
      String targetUrl,
      String adNetworkType,
      long impressions,
      long clicks,
      long costMicros,
      BigDecimal conversions,
      BigDecimal conversionValue) {}

  public record AuctionInsightRow(
      String campaignId,
      String campaignName,
      String domain,
      BigDecimal impressionShare,
      BigDecimal overlapRate,
      BigDecimal positionAboveRate,
      BigDecimal topImpressionPercentage,
      BigDecimal absoluteTopImpressionPercentage,
      BigDecimal outrankingShare) {}

  public record ConversionAttributionRow(
      String campaignId,
      String campaignName,
      String conversionActionName,
      String conversionActionCategory,
      String externalConversionSource,
      BigDecimal conversions,
      BigDecimal conversionValue,
      BigDecimal allConversions,
      BigDecimal allConversionValue) {}

  public record ChangeEventRow(
      String changeDateTime,
      String userEmail,
      String clientType,
      String resourceType,
      String operation,
      String changeResourceName,
      List<String> changedFields,
      String campaignResource,
      String adGroupResource) {

    public ChangeEventRow {
      changedFields = changedFields == null ? List.of() : List.copyOf(changedFields);
    }
  }
}
