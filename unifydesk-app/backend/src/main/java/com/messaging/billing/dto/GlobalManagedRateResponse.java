package com.messaging.billing.dto;

import com.messaging.billing.enums.BillingSku;
import java.math.BigDecimal;
import java.time.Instant;

/** One published global rate interval, denominated in INR per native billing unit. */
public record GlobalManagedRateResponse(
    String rateId,
    BillingSku billingSku,
    BigDecimal price,
    String currency,
    Instant effectiveFrom,
    Instant effectiveTo,
    String createdByUserId,
    String reason) {}
