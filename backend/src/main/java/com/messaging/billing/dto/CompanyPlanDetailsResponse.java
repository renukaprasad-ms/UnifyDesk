package com.messaging.billing.dto;

public record CompanyPlanDetailsResponse(
    CompanySubscriptionResponse subscription, PlanResponse plan) {}
