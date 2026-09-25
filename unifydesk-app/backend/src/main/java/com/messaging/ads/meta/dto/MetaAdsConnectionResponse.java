package com.messaging.ads.meta.dto;

public record MetaAdsConnectionResponse(
    String connectionId,
    String displayName,
    int businessCount,
    int accountCount) {}
