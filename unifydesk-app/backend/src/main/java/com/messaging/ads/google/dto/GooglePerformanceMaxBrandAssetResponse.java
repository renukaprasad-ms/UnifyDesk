package com.messaging.ads.google.dto;

import java.util.List;

public record GooglePerformanceMaxBrandAssetResponse(
    String resourceName,
    String assetResourceName,
    String fieldType,
    String status,
    String primaryStatus,
    List<String> primaryStatusReasons,
    String assetId,
    String assetName,
    String assetType,
    String text,
    String imageUrl) {

  public GooglePerformanceMaxBrandAssetResponse {
    primaryStatusReasons =
        primaryStatusReasons == null ? List.of() : List.copyOf(primaryStatusReasons);
  }
}
