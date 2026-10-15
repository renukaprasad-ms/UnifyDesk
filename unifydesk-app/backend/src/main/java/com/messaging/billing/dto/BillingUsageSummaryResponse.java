package com.messaging.billing.dto;

import java.math.BigDecimal;

public record BillingUsageSummaryResponse(
    long seatsUsed,
    long seatsLimit,
    long contactsUsed,
    long contactsLimit,
    long emailMonthlyUsed,
    long emailMonthlyLimit,
    long smsMonthlyUsed,
    long smsMonthlyLimit,
    BigDecimal walletBalance,
    String walletCurrency) {}
