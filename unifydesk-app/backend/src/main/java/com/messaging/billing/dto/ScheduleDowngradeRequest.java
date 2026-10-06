package com.messaging.billing.dto;

import jakarta.validation.constraints.NotBlank;

public record ScheduleDowngradeRequest(
    @NotBlank String targetPlanId,
    @NotBlank String targetPlanPriceId,
    @NotBlank String idempotencyKey) {}
