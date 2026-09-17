package com.messaging.billing.service;

import com.messaging.billing.enums.BillingCategory;
import com.messaging.billing.enums.PricingDirection;
import com.messaging.billing.enums.UsageUnit;
import com.messaging.channel.enums.ChannelType;
import com.messaging.channel.enums.ProviderType;
import java.time.Instant;

public record UsageChargeRequest(
    Long companyId,
    ChannelType channel,
    ProviderType provider,
    BillingCategory billingCategory,
    PricingDirection direction,
    String country,
    String carrier,
    UsageUnit unit,
    long quantity,
    String sourceType,
    String sourceId,
    String idempotencyKey,
    Instant occurredAt) {}
