package com.messaging.billing.dto;

import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;

public record WalletTopupConfirmRequest(
    @NotBlank String orderId,
    BigDecimal amount,
    String razorpayPaymentId) {

  public WalletTopupConfirmRequest(String orderId) {
    this(orderId, null, null);
  }
}
