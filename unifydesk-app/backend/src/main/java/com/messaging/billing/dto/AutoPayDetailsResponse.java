package com.messaging.billing.dto;

import java.math.BigDecimal;

public record AutoPayDetailsResponse(
    String authorizationId,
    String status,
    BigDecimal maxAmount,
    String currency,
    String providerSubscriptionId,
    String subscriptionSessionId,
    String paymentMethodType,
    String actionUrl) {

  public static AutoPayDetailsResponse notConfigured() {
    return new AutoPayDetailsResponse(null, "NOT_CONFIGURED", BigDecimal.ZERO, "INR", null, null, null, null);
  }
}

