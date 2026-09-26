package com.messaging.ads.google.dto;

public record GoogleAppAssetPerformance(
    String assetResourceName,
    String assetName,
    long impressions,
    long clicks) {}
