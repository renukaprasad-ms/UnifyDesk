package com.messaging.billing.dto;

import com.messaging.billing.enums.BillingCycle;
import com.messaging.billing.enums.PlanStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record UpdatePlanRequest(
    @Size(max = 120) String name,
    BillingCycle billingCycle,
    @DecimalMin("0.000000") BigDecimal basePrice,
    @Size(min = 3, max = 3) String currency,
    PlanStatus status) {}
