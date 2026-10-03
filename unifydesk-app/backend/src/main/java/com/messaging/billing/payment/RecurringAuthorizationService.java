package com.messaging.billing.payment;

import com.messaging.common.exception.ConflictException;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;

@Service
@RequiredArgsConstructor
public class RecurringAuthorizationService {

  private final RecurringAuthorizationStore store;
  private final PaymentProviderGateway gateway;
  private final CashfreeProperties properties;

  public AuthorizationResult prepareOrResolve(
      Long companyId,
      Long subscriptionId,
      BigDecimal canonicalInvoiceAmount,
      String currency,
      PaymentProviderGateway.Customer customer) {
    RecurringAuthorizationStore.Prepared prepared =
        store.prepare(companyId, subscriptionId, canonicalInvoiceAmount, currency);
    if ("ACTIVE".equals(prepared.status())) {
      return result(prepared, null);
    }

    try {
      PaymentProviderGateway.RecurringAuthorization remoteSubscription =
          prepared.newlyCreated()
              ? gateway.createRecurringAuthorization(
                  prepared.providerSubscriptionId(),
                  prepared.maxAmount(),
                  prepared.currency(),
                  customer,
                  properties.getReturnUrl(),
                  ProviderIdempotency.key("subscription", prepared.id()))
              : gateway.fetchRecurringAuthorization(prepared.providerSubscriptionId());

      prepared = store.applySubscription(prepared.id(), remoteSubscription);
      if ("ACTIVE".equals(prepared.status())) {
        return result(prepared, null);
      }
      if (prepared.subscriptionSessionId() == null
          || prepared.subscriptionSessionId().isBlank()) {
        throw new ConflictException(
            "Recurring authorization has no provider checkout continuation");
      }

      PaymentProviderGateway.RemoteSubscriptionPayment authorizationPayment;
      if (!prepared.newlyCreated()) {
        authorizationPayment = fetchAuthorizationOrSubmit(prepared);
      } else {
        authorizationPayment = submitAuthorization(prepared);
      }
      prepared = store.applyAuthorizationPayment(prepared.id(), authorizationPayment);
      return result(prepared, authorizationPayment.actionUrl());
    } catch (RuntimeException exception) {
      store.markAmbiguous(prepared.id());
      throw exception;
    }
  }

  public void bindPayment(long authorizationId, Long paymentId) {
    store.bindPayment(authorizationId, paymentId);
  }

  private PaymentProviderGateway.RemoteSubscriptionPayment fetchAuthorizationOrSubmit(
      RecurringAuthorizationStore.Prepared prepared) {
    try {
      return gateway.fetchSubscriptionPayment(
          prepared.providerSubscriptionId(), prepared.authorizationPaymentId());
    } catch (RestClientResponseException exception) {
      if (exception.getStatusCode().value() != 404) throw exception;
      return submitAuthorization(prepared);
    }
  }

  private PaymentProviderGateway.RemoteSubscriptionPayment submitAuthorization(
      RecurringAuthorizationStore.Prepared prepared) {
    return gateway.authorizeRecurringSubscription(
        prepared.providerSubscriptionId(),
        prepared.subscriptionSessionId(),
        prepared.authorizationPaymentId(),
        PaymentProviderGateway.AuthorizationMethod.HOSTED_UPI_LINK,
        ProviderIdempotency.key("authorization", prepared.id()));
  }

  private AuthorizationResult result(
      RecurringAuthorizationStore.Prepared prepared, String actionUrl) {
    return new AuthorizationResult(
        prepared.id(),
        prepared.providerSubscriptionId(),
        prepared.subscriptionSessionId(),
        prepared.status(),
        actionUrl);
  }

  public record AuthorizationResult(
      long authorizationId,
      String providerSubscriptionId,
      String subscriptionSessionId,
      String status,
      String actionUrl) {}
}
