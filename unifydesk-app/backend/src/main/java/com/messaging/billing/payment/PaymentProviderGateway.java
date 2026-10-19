package com.messaging.billing.payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Provider-neutral money movement boundary. All remote calls must be invoked outside local DB transactions. */
public interface PaymentProviderGateway {

  Checkout createOrder(
      String orderId,
      BigDecimal amount,
      String currency,
      Customer customer,
      String returnUrl,
      String notifyUrl,
      String idempotencyKey);

  Checkout fetchOrder(String orderId);

  List<RemotePayment> fetchOrderPayments(String orderId);

  RemoteRefund createRefund(
      String orderId, String refundId, BigDecimal amount, String note, String idempotencyKey);

  RemoteRefund fetchRefund(String orderId, String refundId);

  RecurringAuthorization createRecurringAuthorization(
      String subscriptionId,
      BigDecimal maxAmount,
      String currency,
      Customer customer,
      String returnUrl,
      String idempotencyKey);

  RecurringAuthorization fetchRecurringAuthorization(String subscriptionId);

  RemoteSubscriptionPayment authorizeRecurringSubscription(
      String subscriptionId,
      String subscriptionSessionId,
      String paymentId,
      AuthorizationMethod method,
      String idempotencyKey);

  RemoteSubscriptionPayment chargeAuthorizedSubscription(
      String subscriptionId,
      String paymentId,
      BigDecimal amount,
      Instant scheduledAt,
      String idempotencyKey);

  RemoteSubscriptionPayment fetchSubscriptionPayment(String subscriptionId, String paymentId);

  default String verifyMandateToken(
      String orderId, String paymentId, String signature, String customerId) {
    throw new UnsupportedOperationException("Provider does not support verified mandates");
  }

  default void cancelRecurringAuthorization(String tokenId, String customerId) {
    throw new UnsupportedOperationException("Provider does not support mandate cancellation");
  }

  enum AuthorizationMethod {
    HOSTED_UPI_LINK
  }

  record Customer(String id, String name, String email, String phone) {}

  record Checkout(String orderId, String paymentSessionId, String status) {}

  record RemotePayment(
      String providerPaymentId,
      String status,
      BigDecimal amount,
      String currency,
      Instant providerCreatedAt,
      String errorCode) {}

  record RemoteRefund(
      String refundId,
      String providerReference,
      String status,
      BigDecimal amount,
      String currency,
      Instant providerCreatedAt,
      Instant providerProcessedAt) {}

  record RecurringAuthorization(
      String subscriptionId,
      String subscriptionSessionId,
      String authorizationStatus,
      String providerSubscriptionReference) {}

  record RemoteSubscriptionPayment(
      String paymentId,
      String providerPaymentId,
      String status,
      BigDecimal amount,
      String currency,
      Instant providerCreatedAt,
      String actionUrl,
      String failureCode) {}
}
