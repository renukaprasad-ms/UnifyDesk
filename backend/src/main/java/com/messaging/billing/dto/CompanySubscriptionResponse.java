package com.messaging.billing.dto;

import com.messaging.billing.enums.SubscriptionStatus;
import java.time.Instant;

public record CompanySubscriptionResponse(
    String subscriptionId,
    String companyId,
    String planId,
    String planCode,
    String planName,
    SubscriptionStatus status,
    Instant startedAt,
    Instant endsAt) {}
