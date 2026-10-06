package com.messaging.billing.dto;

import com.messaging.billing.enums.BillingCycle;
import com.messaging.billing.enums.PlanStatus;
import com.messaging.billing.enums.PlanVisibility;
import java.math.BigDecimal;
import java.util.List;

public record PlanResponse(
    String planId,
    String code,
    String name,
    String description,
    PlanStatus status,
    PlanVisibility visibility,
    BillingCycle billingCycle,
    BigDecimal basePrice,
    String currency,
    int version,
    List<PlanPriceResponse> prices,
    List<EntitlementResponse> entitlements) {}
