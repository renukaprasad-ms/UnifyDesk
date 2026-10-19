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
    String actionUrl,
    String keyId,
    String customerId) {

  public AutoPayDetailsResponse(
      String authorizationId,
      String status,
      BigDecimal maxAmount,
      String currency,
      String providerSubscriptionId,
      String subscriptionSessionId,
      String paymentMethodType,
      String actionUrl,
      String keyId) {
    this(authorizationId, status, maxAmount, currency, providerSubscriptionId, subscriptionSessionId, paymentMethodType, actionUrl, keyId, null);
  }

  public AutoPayDetailsResponse(
      String authorizationId,
      String status,
      BigDecimal maxAmount,
      String currency,
      String providerSubscriptionId,
      String subscriptionSessionId,
      String paymentMethodType,
      String actionUrl) {
    this(authorizationId, status, maxAmount, currency, providerSubscriptionId, subscriptionSessionId, paymentMethodType, actionUrl, null, null);
  }

  public static AutoPayDetailsResponse notConfigured() {
    return new AutoPayDetailsResponse(null, "NOT_CONFIGURED", BigDecimal.ZERO, "INR", null, null, null, null, null, null);
  }
}
