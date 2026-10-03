package com.messaging.billing.dto;

import com.messaging.billing.enums.InvoiceLineType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;

public record InvoiceLineRequest(
    @NotNull InvoiceLineType lineType,
    @NotBlank @Size(max = 500) String description,
    @NotNull @DecimalMin("0.000001") BigDecimal quantity,
    @NotNull BigDecimal unitAmount,
    Instant servicePeriodStart,
    Instant servicePeriodEnd,
    Instant originalServicePeriodStart,
    Instant originalServicePeriodEnd,
    @Size(max = 255) String sourceReference) {}
