package com.messaging.billing.dto;

import com.messaging.billing.enums.BillingCycle;
import com.messaging.billing.enums.RateCardStatus;
import java.math.BigDecimal;
import java.time.Instant;

public record PlanPriceResponse(
    String planPriceId,
    String planId,
    BillingCycle billingCycle,
    BigDecimal amount,
    String currency,
    Instant effectiveFrom,
    Instant effectiveTo,
    RateCardStatus status) {}
