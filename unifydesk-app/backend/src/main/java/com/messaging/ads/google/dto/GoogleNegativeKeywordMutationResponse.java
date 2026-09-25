package com.messaging.ads.google.dto;

public record GoogleNegativeKeywordMutationResponse(
    int requestedCount,
    int createdCount,
    int reusedCount) {}
