package com.messaging.billing.dto;

import com.messaging.billing.enums.BillingCategory;
import com.messaging.billing.enums.CommunicationDirection;
import com.messaging.billing.enums.RateCardStatus;
import com.messaging.billing.enums.UsageUnit;
import com.messaging.channel.enums.ChannelType;
import com.messaging.channel.enums.ProviderType;
import java.math.BigDecimal;
import java.time.Instant;

public record RateCardResponse(
    String rateCardId,
    String planId,
    ChannelType channel,
    ProviderType provider,
    String country,
    String carrier,
    BillingCategory billingCategory,
    CommunicationDirection direction,
    UsageUnit unit,
    BigDecimal price,
    String currency,
    Instant effectiveFrom,
    Instant effectiveTo,
    RateCardStatus status) {}
