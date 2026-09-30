package com.messaging.ads.google.dto;

public record GoogleCampaignPortfolioAssignmentResponse(
    String campaignId,
    String biddingStrategyResourceName,
    String campaignBudgetResourceName,
    String biddingStrategyType,
    boolean portfolioCompatible) {}
