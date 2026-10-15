package com.messaging.billing.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record WalletTopupOrderRequest(
    @NotNull @DecimalMin("10.00") BigDecimal amount,
    String currency,
    WalletCustomerDetails customer) {

  public record WalletCustomerDetails(String name, String email, String phone) {}
}
