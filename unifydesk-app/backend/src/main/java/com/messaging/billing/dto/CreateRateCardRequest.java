package com.messaging.billing.dto;

import com.messaging.billing.enums.BillingCategory;
import com.messaging.billing.enums.CommunicationDirection;
import com.messaging.billing.enums.UsageUnit;
import com.messaging.channel.enums.ChannelType;
import com.messaging.channel.enums.ProviderType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;

public record CreateRateCardRequest(
    @NotBlank String planId,
    @NotNull ChannelType channel,
    @NotNull ProviderType provider,
    @Size(max = 2) String country,
    @Size(max = 80) String carrier,
    @NotNull BillingCategory billingCategory,
    @NotNull CommunicationDirection direction,
    @NotNull UsageUnit unit,
    @NotNull @DecimalMin("0.000000") BigDecimal price,
    @Size(min = 3, max = 3) String currency,
    @NotNull Instant effectiveFrom,
    Instant effectiveTo) {}
