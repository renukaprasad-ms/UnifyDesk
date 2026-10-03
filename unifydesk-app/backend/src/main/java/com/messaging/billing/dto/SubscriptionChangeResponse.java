package com.messaging.billing.dto;

import com.messaging.billing.enums.SubscriptionChangeStatus;
import com.messaging.billing.enums.SubscriptionChangeType;
import java.math.BigDecimal;
import java.time.Instant;

public record SubscriptionChangeResponse(
    String changeId,
    String companyId,
    String subscriptionId,
    String fromPlanId,
    String toPlanId,
    SubscriptionChangeType type,
    SubscriptionChangeStatus status,
    Instant requestedAt,
    Instant effectiveAt,
    String quoteReference,
    String invoiceId,
    String currency,
    BigDecimal currentPrice,
    BigDecimal targetPrice,
    BigDecimal targetProratedCharge,
    BigDecimal unusedCurrentCredit,
    BigDecimal netAmount,
    BigDecimal amountDue,
    BigDecimal creditAmount) {}
