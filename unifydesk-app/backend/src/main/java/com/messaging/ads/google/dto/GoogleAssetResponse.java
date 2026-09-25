package com.messaging.ads.google.dto;

public record GoogleAssetResponse(
    String id,
    String resourceName,
    String name,
    String type,
    String text,
    String imageUrl,
    Long imageWidth,
    Long imageHeight,
    String youtubeVideoId) {}
