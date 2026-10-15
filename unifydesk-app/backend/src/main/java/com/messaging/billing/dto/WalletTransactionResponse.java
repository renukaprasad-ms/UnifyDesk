package com.messaging.billing.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record WalletTransactionResponse(
    String id,
    String companyId,
    String type,
    BigDecimal amount,
    BigDecimal balanceAfter,
    String currency,
    String referenceId,
    String description,
    Long actorUserId,
    Instant createdAt) {}
