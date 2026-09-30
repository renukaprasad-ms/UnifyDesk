package com.messaging.ads.google.dto;

public record GooglePortfolioBiddingStrategyResponse(
    String id,
    String resourceName,
    String name,
    String type,
    String status,
    Long campaignCount,
    Long nonRemovedCampaignCount,
    String alignedCampaignBudgetId,
    Long targetCpaMicros,
    Double targetRoas,
    Long impressions,
    Long clicks,
    Double conversions,
    Double conversionsValue,
    Long costMicros) {}
