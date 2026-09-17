package com.messaging.billing.dto;

import com.messaging.billing.enums.BillingCycle;
import com.messaging.billing.enums.PlanPriceStatus;
import java.math.BigDecimal;
import java.time.Instant;

public record PlanPriceResponse(
    String planPriceId,
    BillingCycle billingCycle,
    BigDecimal amount,
    String currency,
    Instant effectiveFrom,
    Instant effectiveTo,
    PlanPriceStatus status) {}
