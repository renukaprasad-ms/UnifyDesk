package com.messaging.billing.dto;

import com.messaging.common.validation.ValidPhone;

import jakarta.validation.constraints.DecimalMin;
import java.math.BigDecimal;

public record AutoPaySetupRequest(
    String name,
    String email,
    @ValidPhone String phone,
    String returnUrl,
    @DecimalMin(value = "1.00", message = "Max amount must be at least ₹1.00")
    BigDecimal maxAmount) {

  public BigDecimal resolvedMaxAmount() {
    return maxAmount != null ? maxAmount : new BigDecimal("15000.00");
  }
}
