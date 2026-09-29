package com.messaging.ads.google.dto;

import jakarta.validation.constraints.NotBlank;

public record GooglePortfolioAlignmentRequest(
    @NotBlank String biddingStrategyId,
    @NotBlank String sharedBudgetId) {}
