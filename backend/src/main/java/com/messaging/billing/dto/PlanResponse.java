package com.messaging.billing.dto;

import com.messaging.billing.enums.BillingCycle;
import com.messaging.billing.enums.PlanStatus;
import java.math.BigDecimal;
import java.util.List;

public record PlanResponse(
    String planId,
    String code,
    String name,
    PlanStatus status,
    BillingCycle billingCycle,
    BigDecimal basePrice,
    String currency,
    int version,
    List<EntitlementResponse> entitlements) {}
