package com.messaging.billing.dto;

import com.messaging.billing.enums.BillingCategory;
import com.messaging.billing.enums.CommunicationDirection;
import com.messaging.billing.enums.RateCardStatus;
import com.messaging.billing.enums.UsageUnit;
import com.messaging.channel.enums.ChannelType;
import com.messaging.channel.enums.ProviderType;
import java.math.BigDecimal;
import java.time.Instant;

public record CompanyRateOverrideResponse(
    String overrideId,
    String companyId,
    ChannelType channel,
    ProviderType provider,
    BillingCategory billingCategory,
    CommunicationDirection direction,
    String country,
    String carrier,
    UsageUnit unit,
    BigDecimal price,
    String currency,
    Instant effectiveFrom,
    Instant effectiveTo,
    RateCardStatus status,
    String createdByUserId,
    String reason) {}
