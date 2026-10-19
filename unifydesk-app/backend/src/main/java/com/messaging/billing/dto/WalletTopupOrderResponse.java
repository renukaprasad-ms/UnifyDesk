package com.messaging.billing.dto;

import java.math.BigDecimal;

public record WalletTopupOrderResponse(
    String orderId,
    String paymentSessionId,
    BigDecimal amount,
    String currency,
    String status,
    String razorpayOrderId,
    String keyId,
    Long amountInPaise) {

  public WalletTopupOrderResponse(
      String orderId,
      String paymentSessionId,
      BigDecimal amount,
      String currency,
      String status) {
    this(
        orderId,
        paymentSessionId,
        amount,
        currency,
        status,
        orderId,
        null,
        amount != null ? amount.multiply(BigDecimal.valueOf(100)).longValue() : null);
  }
}
