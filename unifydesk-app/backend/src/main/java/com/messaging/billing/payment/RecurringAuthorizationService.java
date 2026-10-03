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
    return resolve(
        store.prepare(companyId, subscriptionId, canonicalInvoiceAmount, currency), customer);
  }

  /**
   * Creates/offers a future recurring mandate separately from the initial one-time collection.
   * The provider identity is keyed to the durable B1 checkout intent until activation creates the
   * CompanySubscription.
   */
  public AuthorizationResult prepareForInitialCheckout(
      Long companyId,
      Long checkoutIntentId,
      BigDecimal canonicalInvoiceAmount,
      String currency,
      PaymentProviderGateway.Customer customer) {
    return resolve(
        store.prepareInitial(companyId, checkoutIntentId, canonicalInvoiceAmount, currency),
        customer);
  }

  /**
   * Re-establishes provider truth for an already-bound authorization without creating a new
   * provider subscription. Used by the automatic renewal boundary before any CHARGE is launched.
   */
  public AuthorizationResult refreshBound(Long companyId, Long subscriptionId) {
    RecurringAuthorizationStore.Prepared prepared = store.bound(companyId, subscriptionId);
    if (prepared == null) {
      return null;
    }
    if ("ACTIVE".equals(prepared.status())) {
      return result(prepared, null);
    }
    try {
      PaymentProviderGateway.RecurringAuthorization remote =
          gateway.fetchRecurringAuthorization(prepared.providerSubscriptionId());
      prepared = store.applySubscription(prepared.id(), remote);
      return result(prepared, null);
    } catch (RuntimeException exception) {
      store.markAmbiguous(prepared.id());
      throw exception;
    }
  }

  public void bindInitialActivation(
      Long companyId, Long checkoutIntentId, Long subscriptionId) {
    store.bindInitialActivation(companyId, checkoutIntentId, subscriptionId);
  }

  public void bindPayment(long authorizationId, Long paymentId) {
    store.bindPayment(authorizationId, paymentId);
  }

  private AuthorizationResult resolve(
      RecurringAuthorizationStore.Prepared prepared,
      PaymentProviderGateway.Customer customer) {
    if ("ACTIVE".equals(prepared.status())) {
      return result(prepared, null);
    }

    try {
      PaymentProviderGateway.RecurringAuthorization remoteSubscription =
          createOrRecoverSubscription(prepared, customer);
      prepared = store.applySubscription(prepared.id(), remoteSubscription);
      if ("ACTIVE".equals(prepared.status())) {
        return result(prepared, null);
      }
      if (prepared.subscriptionSessionId() == null
          || prepared.subscriptionSessionId().isBlank()) {
        throw new ConflictException(
            "Recurring authorization has no provider checkout continuation");
      }

      PaymentProviderGateway.RemoteSubscriptionPayment authorizationPayment =
          fetchAuthorizationOrSubmit(prepared);
      prepared = store.applyAuthorizationPayment(prepared.id(), authorizationPayment);
      return result(prepared, authorizationPayment.actionUrl());
    } catch (RuntimeException exception) {
      store.markAmbiguous(prepared.id());
      throw exception;
    }
  }

  private PaymentProviderGateway.RecurringAuthorization createOrRecoverSubscription(
      RecurringAuthorizationStore.Prepared prepared,
      PaymentProviderGateway.Customer customer) {
    if (prepared.newlyCreated()) {
      return createSubscription(prepared, customer);
    }
    try {
      return gateway.fetchRecurringAuthorization(prepared.providerSubscriptionId());
    } catch (RestClientResponseException exception) {
      if (exception.getStatusCode().value() != 404) {
        throw exception;
      }
      return createSubscription(prepared, customer);
    }
  }

  private PaymentProviderGateway.RecurringAuthorization createSubscription(
      RecurringAuthorizationStore.Prepared prepared,
      PaymentProviderGateway.Customer customer) {
    return gateway.createRecurringAuthorization(
        prepared.providerSubscriptionId(),
        prepared.maxAmount(),
        prepared.currency(),
        customer,
        properties.getReturnUrl(),
        ProviderIdempotency.key("subscription", prepared.id()));
  }

  private PaymentProviderGateway.RemoteSubscriptionPayment fetchAuthorizationOrSubmit(
      RecurringAuthorizationStore.Prepared prepared) {
    try {
      return gateway.fetchSubscriptionPayment(
          prepared.providerSubscriptionId(), prepared.authorizationPaymentId());
    } catch (RestClientResponseException exception) {
      if (exception.getStatusCode().value() != 404) {
        throw exception;
      }
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
