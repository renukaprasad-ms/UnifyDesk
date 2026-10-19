package com.messaging.billing.dto;

public record AutoPayVerifyRequest(
    String paymentId,
    String orderId,
    String signature) {}
