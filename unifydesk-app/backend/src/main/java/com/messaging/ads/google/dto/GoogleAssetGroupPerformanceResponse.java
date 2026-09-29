package com.messaging.ads.google.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

public record GoogleAssetGroupPerformanceResponse(
    String id,
    String resourceName,
    String name,
    String status,
    String primaryStatus,
    List<String> primaryStatusReasons,
    String adStrength,
    JsonNode assetCoverage,
    Long impressions,
    Long clicks,
    Double conversions,
    Double conversionsValue,
    Long costMicros) {

  public GoogleAssetGroupPerformanceResponse {
    primaryStatusReasons =
        primaryStatusReasons == null ? List.of() : List.copyOf(primaryStatusReasons);
  }
}
