package com.messaging.ads.campaign.dto;

import com.messaging.ads.campaign.enums.BudgetAllocationMode;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record UpsertBudgetPlanRequest(
    @NotNull @Positive Long totalBudgetMinor,
    @NotNull BudgetAllocationMode allocationMode) {}
