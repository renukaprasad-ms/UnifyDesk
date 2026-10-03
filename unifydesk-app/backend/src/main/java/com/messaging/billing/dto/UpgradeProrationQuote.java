package com.messaging.billing.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record UpgradeProrationQuote(
    String currency,
    Instant effectiveAt,
    Instant currentPeriodStart,
    Instant currentPeriodEnd,
    BigDecimal currentPrice,
    BigDecimal targetPrice,
    BigDecimal targetProratedCharge,
    BigDecimal unusedCurrentCredit,
    BigDecimal netAmount,
    BigDecimal amountDue,
    BigDecimal creditAmount) {}
