package com.messaging.ads.google.dto;

public record GoogleAssetGroupAssetResponse(
    String resourceName,
    String assetResourceName,
    String assetId,
    String assetName,
    String assetType,
    String fieldType,
    String status,
    String primaryStatus,
    String policyApprovalStatus,
    String text,
    String imageUrl,
    Long imageWidth,
    Long imageHeight,
    String youtubeVideoId) {}
