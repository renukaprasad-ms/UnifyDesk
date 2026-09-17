package com.messaging.billing.dto;

import com.messaging.billing.enums.SubscriptionStatus;
import java.time.Instant;

public record CompanySubscriptionResponse(
    String subscriptionId,
    String companyId,
    String planId,
    String planCode,
    String planName,
    String planPriceId,
    SubscriptionStatus status,
    Instant startedAt,
    Instant endsAt,
    Instant currentPeriodStart,
    Instant currentPeriodEnd,
    Instant trialEndsAt,
    boolean cancelAtPeriodEnd,
    Instant cancelledAt) {}
