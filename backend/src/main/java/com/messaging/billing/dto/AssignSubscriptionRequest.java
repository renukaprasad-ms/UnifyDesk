package com.messaging.billing.dto;

import com.messaging.billing.enums.SubscriptionStatus;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record AssignSubscriptionRequest(
    @NotNull String planId,
    String planPriceId,
    SubscriptionStatus status,
    Instant startedAt,
    Instant endsAt,
    Instant currentPeriodStart,
    Instant currentPeriodEnd,
    Instant trialEndsAt,
    Boolean cancelAtPeriodEnd) {}
