package com.messaging.billing.dto;

import com.messaging.billing.enums.SubscriptionStatus;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;

public record AssignSubscriptionRequest(
    @NotBlank String planId,
    String planPriceId,
    SubscriptionStatus status,
    Instant startedAt,
    Instant endsAt,
    Instant trialEndsAt,
    Instant currentPeriodEnd) {}
