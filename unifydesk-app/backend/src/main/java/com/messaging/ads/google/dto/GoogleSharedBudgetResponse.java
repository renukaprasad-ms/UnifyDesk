package com.messaging.ads.google.dto;

public record GoogleSharedBudgetResponse(
    String id,
    String resourceName,
    String name,
    String status,
    String deliveryMethod,
    Long amountMicros,
    Long referenceCount,
    String alignedBiddingStrategyId,
    Long impressions,
    Long clicks,
    Double conversions,
    Double conversionsValue,
    Long costMicros) {}
