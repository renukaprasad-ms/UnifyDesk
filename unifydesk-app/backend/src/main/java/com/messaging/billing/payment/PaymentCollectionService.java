package com.messaging.billing.payment;

import com.messaging.billing.enums.PaymentPurpose;
import com.messaging.billing.enums.PaymentStatus;
import com.messaging.common.exception.BadRequestException;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;

@Service
@RequiredArgsConstructor
public class PaymentCollectionService {

  private final PaymentCollectionStore store;
  private final PaymentProviderGateway gateway;
  private final CashfreeProperties cashfreeProperties;

  /**
   * Durable orchestration boundary:
   * local intent commit -> remote provider call -> local provider result commit.
   */
  public CheckoutResult createOrResolve(
      Long companyId,
      Long invoiceId,
      PaymentPurpose purpose,
      String idempotencyKey,
      Long checkoutIntentId,
      Long subscriptionChangeId,
      Long subscriptionId,
      PaymentProviderGateway.Customer customer) {
    String key = required(idempotencyKey);
    PaymentCollectionStore.Prepared prepared =
        store.prepare(
            companyId,
            invoiceId,
            purpose,
            key,
            checkoutIntentId,
            subscriptionChangeId,
            subscriptionId);

    if (prepared.paymentSessionId() != null && !prepared.paymentSessionId().isBlank()) {
      return toResult(prepared);
    }

    try {
      PaymentProviderGateway.Checkout checkout;
      if (prepared.newlyCreated()) {
        checkout = createRemote(prepared, customer);
      } else {
        checkout = recoverOrCreateIfDefinitelyAbsent(prepared, customer);
      }
      return toResult(store.acceptProviderOrder(prepared.paymentId(), checkout));
    } catch (RuntimeException exception) {
      store.markProviderOrderAmbiguous(prepared.paymentId());
      throw exception;
    }
  }

  private PaymentProviderGateway.Checkout recoverOrCreateIfDefinitelyAbsent(
      PaymentCollectionStore.Prepared prepared, PaymentProviderGateway.Customer customer) {
    try {
      return gateway.fetchOrder(prepared.providerOrderId());
    } catch (RestClientResponseException exception) {
      if (exception.getStatusCode().value() != 404) throw exception;
      // A provider 404 is a definitive absence signal. Re-submit with the same order identity
      // and the same deterministic idempotency key; never generate a second provider identity.
      return createRemote(prepared, customer);
    }
  }

  private PaymentProviderGateway.Checkout createRemote(
      PaymentCollectionStore.Prepared prepared, PaymentProviderGateway.Customer customer) {
    return gateway.createOrder(
        prepared.providerOrderId(),
        prepared.amount(),
        prepared.currency(),
        customer,
        cashfreeProperties.getReturnUrl(),
        cashfreeProperties.getNotifyUrl(),
        ProviderIdempotency.key("order", prepared.paymentId()));
  }

  private CheckoutResult toResult(PaymentCollectionStore.Prepared payment) {
    return new CheckoutResult(
        payment.paymentId(),
        payment.invoiceId(),
        payment.providerOrderId(),
        payment.paymentSessionId(),
        payment.amount(),
        payment.currency(),
        payment.status(),
        null,
        null);
  }

  private String required(String value) {
    if (value == null || value.isBlank() || value.length() > 255) {
      throw new BadRequestException("A valid payment idempotency key is required");
    }
    return value.trim();
  }

  public record CheckoutResult(
      Long paymentId,
      Long invoiceId,
      String providerOrderId,
      String paymentSessionId,
      BigDecimal amount,
      String currency,
      PaymentStatus status,
      String recurringAuthorizationSessionId,
      String recurringAuthorizationActionUrl) {

    public CheckoutResult withRecurringAuthorization(String sessionId, String actionUrl) {
      return new CheckoutResult(
          paymentId,
          invoiceId,
          providerOrderId,
          paymentSessionId,
          amount,
          currency,
          status,
          sessionId,
          actionUrl);
    }
  }
}
