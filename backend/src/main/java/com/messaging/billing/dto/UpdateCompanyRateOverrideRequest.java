package com.messaging.billing.dto;

import com.messaging.billing.enums.RateCardStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;

public record UpdateCompanyRateOverrideRequest(
    @DecimalMin("0.000000") BigDecimal price,
    @Size(min = 3, max = 3) String currency,
    Instant effectiveFrom,
    Instant effectiveTo,
    RateCardStatus status,
    @Size(max = 500) String reason) {}
