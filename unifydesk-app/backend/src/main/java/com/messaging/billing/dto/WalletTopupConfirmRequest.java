package com.messaging.billing.dto;

import jakarta.validation.constraints.NotBlank;

public record WalletTopupConfirmRequest(@NotBlank String orderId) {}
