package com.messaging.ads.google.dto;

import java.util.List;

public record GoogleAssetGroupAssetResponse(
    String resourceName,
    String assetResourceName,
    String assetId,
    String assetName,
    String assetType,
    String fieldType,
    String status,
    String primaryStatus,
    List<String> primaryStatusReasons,
    String source,
    String policyApprovalStatus,
    String policyReviewStatus,
    String text,
    String imageUrl,
    Long imageWidth,
    Long imageHeight,
    String youtubeVideoId) {

  public GoogleAssetGroupAssetResponse {
    primaryStatusReasons =
        primaryStatusReasons == null ? List.of() : List.copyOf(primaryStatusReasons);
  }
}
