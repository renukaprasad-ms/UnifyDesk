package com.messaging.billing.dto;

import java.math.BigDecimal;

public record ConfigureAutoRechargeRequest(
    boolean enabled,
    BigDecimal threshold,
    BigDecimal amount) {}
