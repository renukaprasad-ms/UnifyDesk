package com.messaging.ads.campaign.dto;

import com.messaging.ads.campaign.entity.BudgetPlan;
import com.messaging.ads.campaign.enums.BudgetAllocationMode;
import com.messaging.ads.campaign.enums.BudgetPeriod;
import java.time.Instant;

public record BudgetPlanResponse(
    String id,
    String campaignId,
    String currency,
    Long totalBudgetMinor,
    BudgetPeriod budgetPeriod,
    BudgetAllocationMode allocationMode,
    Instant createdAt,
    Instant updatedAt) {

  public static BudgetPlanResponse from(BudgetPlan plan, String currency) {
    return new BudgetPlanResponse(
        plan.getId().toString(),
        plan.getReachlyoCampaignId().toString(),
        currency,
        plan.getTotalBudgetMinor(),
        plan.getBudgetPeriod(),
        plan.getAllocationMode(),
        plan.getCreatedAt(),
        plan.getUpdatedAt());
  }
}
