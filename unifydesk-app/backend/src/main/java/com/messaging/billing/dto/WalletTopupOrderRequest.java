package com.messaging.billing.dto;

import com.messaging.common.validation.ValidPhone;
import jakarta.validation.Valid;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record WalletTopupOrderRequest(
    @NotNull @DecimalMin("10.00") BigDecimal amount,
    String currency,
    @Valid WalletCustomerDetails customer) {

  public record WalletCustomerDetails(String name, String email, @ValidPhone String phone) {}
}
