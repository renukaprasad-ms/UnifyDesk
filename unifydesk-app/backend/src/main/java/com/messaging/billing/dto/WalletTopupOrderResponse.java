package com.messaging.billing.dto;

import java.math.BigDecimal;

public record WalletTopupOrderResponse(
    String orderId,
    String paymentSessionId,
    BigDecimal amount,
    String currency,
    String status) {}
