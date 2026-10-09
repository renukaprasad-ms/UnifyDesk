package com.messaging.billing.dto;

import com.messaging.billing.enums.BillingSku;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;

public record ReviseGlobalManagedRateRequest(
    @NotNull BillingSku billingSku,
    @NotNull @DecimalMin("0.000000") @Digits(integer = 13, fraction = 6) BigDecimal price,
    Instant effectiveFrom,
    @Size(max = 500) String reason) {}
