package com.messaging.billing.dto;

import com.messaging.billing.enums.BillingCycle;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;

public record CreatePlanPriceRequest(
    @NotNull BillingCycle billingCycle,
    @NotNull @DecimalMin("0.000000") BigDecimal amount,
    @NotNull @Size(min = 3, max = 3) String currency,
    @NotNull Instant effectiveFrom,
    Instant effectiveTo) {}
