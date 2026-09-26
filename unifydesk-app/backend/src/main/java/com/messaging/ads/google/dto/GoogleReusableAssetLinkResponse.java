package com.messaging.ads.google.dto;

public record GoogleReusableAssetLinkResponse(
    String resourceName,
    String assetResourceName,
    String assetId,
    String assetName,
    String assetType,
    String fieldType,
    String level,
    String targetId,
    String status,
    String primaryStatus) {}
