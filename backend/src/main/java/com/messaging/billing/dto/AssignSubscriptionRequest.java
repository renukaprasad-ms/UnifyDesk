package com.messaging.billing.dto;

import com.messaging.billing.enums.SubscriptionStatus;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record AssignSubscriptionRequest(
    @NotNull String planId, SubscriptionStatus status, Instant startedAt, Instant endsAt) {}
