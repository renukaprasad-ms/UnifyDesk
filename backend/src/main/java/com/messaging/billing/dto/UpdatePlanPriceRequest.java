package com.messaging.billing.dto;

import com.messaging.billing.enums.PlanPriceStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;

public record UpdatePlanPriceRequest(
    @DecimalMin("0.000000") BigDecimal amount,
    @Size(min = 3, max = 3) String currency,
    Instant effectiveFrom,
    Instant effectiveTo,
    PlanPriceStatus status) {}
