package com.messaging.billing.dto;

import com.messaging.billing.enums.ChannelType;
import com.messaging.billing.enums.CommunicationDirection;
import com.messaging.billing.enums.MessageType;
import com.messaging.billing.enums.UsageUnit;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;

public record CreateRateCardRequest(
    @NotNull Long planId,
    @NotNull ChannelType channel,
    @Size(max = 80) String provider,
    @Size(max = 2) String country,
    @Size(max = 80) String carrier,
    @NotNull MessageType messageType,
    @NotNull CommunicationDirection direction,
    @NotNull UsageUnit unit,
    @NotNull @DecimalMin("0.000000") BigDecimal price,
    @Size(min = 3, max = 3) String currency,
    Instant effectiveFrom,
    Instant effectiveTo) {}
