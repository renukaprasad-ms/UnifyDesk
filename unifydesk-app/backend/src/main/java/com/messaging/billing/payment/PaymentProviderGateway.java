package com.messaging.billing.payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public interface PaymentProviderGateway {

  Checkout createOrder(
      String orderId,
      BigDecimal amount,
      String currency,
      Customer customer,
      String returnUrl,
      String notifyUrl,
      String idempotencyKey);

  List<RemotePayment> fetchOrderPayments(String orderId);

  RemoteRefund createRefund(
      String orderId, String refundId, BigDecimal amount, String note, String idempotencyKey);

  OnDemandSubscription createOnDemandSubscription(
      String subscriptionId,
      BigDecimal maxAmount,
      String currency,
      Customer customer,
      String returnUrl,
      String idempotencyKey);

  OnDemandSubscription fetchSubscription(String subscriptionId);

  record Customer(String id, String name, String email, String phone) {}

  record Checkout(String orderId, String paymentSessionId, String status) {}

  record RemotePayment(
      String providerPaymentId,
      String status,
      BigDecimal amount,
      String currency,
      Instant providerCreatedAt,
      String errorCode) {}

  record RemoteRefund(String providerRefundId, String status) {}

  record OnDemandSubscription(
      String subscriptionId, String subscriptionSessionId, String authorizationStatus) {}
}
