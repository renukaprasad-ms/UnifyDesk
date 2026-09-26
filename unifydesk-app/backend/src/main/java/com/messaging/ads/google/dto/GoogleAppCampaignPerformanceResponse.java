package com.messaging.ads.google.dto;

import java.util.List;

public record GoogleAppCampaignPerformanceResponse(
    long impressions,
    long clicks,
    long costMicros,
    double conversions,
    double allConversions,
    long skAdNetworkInstalls,
    long skAdNetworkTotalConversions,
    List<GoogleAppAssetPerformance> assetPerformance) {

  public GoogleAppCampaignPerformanceResponse {
    assetPerformance = assetPerformance == null ? List.of() : List.copyOf(assetPerformance);
  }
}
