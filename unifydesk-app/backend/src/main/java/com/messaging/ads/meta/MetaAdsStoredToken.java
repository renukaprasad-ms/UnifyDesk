package com.messaging.ads.meta;

public record MetaAdsStoredToken(
    String accessToken,
    String tokenType,
    String graphApiVersion,
    String externalUserId) {}
