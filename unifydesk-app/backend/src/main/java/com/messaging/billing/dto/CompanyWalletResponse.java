package com.messaging.billing.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record CompanyWalletResponse(
    String companyId,
    BigDecimal balance,
    String currency,
    boolean autoRechargeEnabled,
    BigDecimal autoRechargeThreshold,
    BigDecimal autoRechargeAmount,
    int autoRechargeFailedAttempts,
    boolean autoRechargePaused,
    Instant updatedAt) {

  public CompanyWalletResponse(
      String companyId,
      BigDecimal balance,
      String currency,
      boolean autoRechargeEnabled,
      BigDecimal autoRechargeThreshold,
      BigDecimal autoRechargeAmount,
      Instant updatedAt) {
    this(
        companyId,
        balance,
        currency,
        autoRechargeEnabled,
        autoRechargeThreshold,
        autoRechargeAmount,
        0,
        false,
        updatedAt);
  }
}
