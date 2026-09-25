package com.messaging.ads.google.dto;

public record GoogleAdsConnectionResponse(
    String connectionId,
    String displayName,
    int accountCount,
    int managerAccountCount,
    int testAccountCount) {}
