package com.messaging.billing.dto;

import com.messaging.billing.enums.BillingCycle;
import com.messaging.billing.enums.PlanVisibility;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record CreatePlanRequest(
    @NotBlank @Size(max = 64) String code,
    @NotBlank @Size(max = 120) String name,
    @Size(max = 500) String description,
    @NotNull BillingCycle billingCycle,
    @NotNull @DecimalMin("0.000000") BigDecimal basePrice,
    @NotBlank @Size(min = 3, max = 3) String currency,
    int version,
    PlanVisibility visibility) {}
