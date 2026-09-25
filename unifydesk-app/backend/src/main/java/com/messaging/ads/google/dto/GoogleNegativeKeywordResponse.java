package com.messaging.ads.google.dto;

public record GoogleNegativeKeywordResponse(
    String resourceName,
    String criterionId,
    String text,
    String matchType,
    String status) {}
