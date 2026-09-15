package com.messaging.billing.dto;

import com.messaging.billing.enums.RateCardStatus;
import jakarta.validation.constraints.DecimalMin;
import java.math.BigDecimal;
import java.time.Instant;

public record UpdateRateCardRequest(
    @DecimalMin("0.000000") BigDecimal price,
    Instant effectiveFrom,
    Instant effectiveTo,
    RateCardStatus status) {}
