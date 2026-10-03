package com.messaging.billing.dto;

import com.messaging.billing.enums.SubscriptionCheckoutIntentStatus;
import java.math.BigDecimal;
import java.time.Instant;

public record SubscriptionCheckoutIntentResponse(
    String intentId,
    String companyId,
    String planId,
    String planPriceId,
    String billingPeriodId,
    String invoiceId,
    String subscriptionId,
    SubscriptionCheckoutIntentStatus status,
    String currency,
    BigDecimal amount,
    Instant servicePeriodStart,
    Instant servicePeriodEnd,
    Instant requestedAt,
    Instant activatedAt) {}
