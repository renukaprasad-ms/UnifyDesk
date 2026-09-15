package com.messaging.billing.dto;

import com.messaging.billing.enums.ChannelType;
import com.messaging.billing.enums.CommunicationDirection;
import com.messaging.billing.enums.MessageType;
import com.messaging.billing.enums.RateCardStatus;
import com.messaging.billing.enums.UsageUnit;
import java.math.BigDecimal;
import java.time.Instant;

public record RateCardResponse(
    String rateCardId,
    String planId,
    ChannelType channel,
    String provider,
    String country,
    String carrier,
    MessageType messageType,
    CommunicationDirection direction,
    UsageUnit unit,
    BigDecimal price,
    String currency,
    Instant effectiveFrom,
    Instant effectiveTo,
    RateCardStatus status) {}
