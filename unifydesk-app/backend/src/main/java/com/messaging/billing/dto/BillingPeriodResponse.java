package com.messaging.billing.dto;

import com.messaging.billing.enums.BillingCycle;
import com.messaging.billing.enums.BillingPeriodKind;
import com.messaging.billing.enums.BillingPeriodStatus;
import java.time.Instant;

public record BillingPeriodResponse(
    String billingPeriodId,
    String companyId,
    BillingPeriodKind kind,
    BillingCycle cadence,
    Instant startAt,
    Instant endAt,
    BillingPeriodStatus status,
    Instant finalizedAt) {}
