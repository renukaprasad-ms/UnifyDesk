package com.messaging.billing.dto;

import com.messaging.billing.enums.PaymentPolicy;
import com.messaging.billing.enums.SubscriptionStatus;
import java.time.Instant;

public record CompanySubscriptionResponse(
    String subscriptionId,
    String companyId,
    String planId,
    String planPriceId,
    String planCode,
    String planName,
    SubscriptionStatus status,
    Instant startedAt,
    Instant endsAt,
    Instant currentPeriodStart,
    Instant currentPeriodEnd,
    Instant billingAnchor,
    Instant trialEndsAt,
    Instant graceEndsAt,
    boolean cancelAtPeriodEnd,
    Instant cancelledAt,
    Instant cancellationEffectiveAt,
    PaymentPolicy paymentPolicy,
    String externalContractReference) {}
