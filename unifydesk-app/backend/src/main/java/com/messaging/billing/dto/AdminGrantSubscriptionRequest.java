package com.messaging.billing.dto;

import com.messaging.billing.enums.PaymentPolicy;
import com.messaging.billing.enums.SubscriptionStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record AdminGrantSubscriptionRequest(
    @NotBlank String planId,
    String planPriceId,
    SubscriptionStatus status,
    Instant startedAt,
    Instant endsAt,
    Instant trialEndsAt,
    Instant currentPeriodEnd,
    @NotNull PaymentPolicy paymentPolicy,
    @NotBlank @Size(max = 1000) String reason,
    @Size(max = 255) String externalContractReference) {}
