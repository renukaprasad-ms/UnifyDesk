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
  private final RazorpayProperties properties;

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
   * Creates/offers a future recurring mandate separately from the initial one-time collection. The
   * provider identity is keyed to the durable B1 checkout intent until activation creates the
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

  public AuthorizationResult prepareCompanyAutopay(
      Long companyId,
      BigDecimal maxAmount,
      String currency,
      PaymentProviderGateway.Customer customer) {
    if (!properties.isRecurringEnabled()) {
      throw new ConflictException(
          "Razorpay recurring mandate setup is not enabled for this merchant");
    }
    BigDecimal limit = maxAmount != null ? maxAmount : new BigDecimal("15000.00");
    String curr = currency != null && !currency.isBlank() ? currency : "INR";
    RecurringAuthorizationStore.Prepared prepared =
        store.prepareCompanyAutopay(companyId, limit, curr);
    if ("ACTIVE".equalsIgnoreCase(prepared.status())) {
      return result(prepared, null);
    }
    // Setup calls must reuse an existing valid authorization order. A local UDS-AP
    // identifier is never a Razorpay order or a confirmed recurring token.
    if (!prepared.newlyCreated()) {
      if (hasProviderCheckout(prepared)) {
        return result(prepared, null);
      }
      throw new ConflictException(
          "AutoPay authorization is incomplete or being prepared; retry later");
    }
    PaymentProviderGateway.RecurringAuthorization authorization;
    try {
      authorization = createSubscription(prepared, customer);
      if (authorization == null
          || !prepared.providerSubscriptionId().equals(authorization.subscriptionId())
          || authorization.subscriptionSessionId() == null
          || !authorization.subscriptionSessionId().startsWith("order_")
          || authorization.providerSubscriptionReference() == null
          || !authorization.providerSubscriptionReference().startsWith("cust_")) {
        throw new ConflictException("Razorpay did not provide a valid mandate order and customer");
      }
      prepared = store.applySubscription(prepared.id(), authorization);
      return result(prepared, null);
    } catch (RuntimeException exception) {
      store.markAmbiguous(prepared.id());
      throw exception;
    }
  }

  public AuthorizationResult getActiveCompanyAutopay(Long companyId) {
    RecurringAuthorizationStore.Prepared prepared = store.findActiveCompanyAutopay(companyId);
    if (prepared == null) {
      return null;
    }
    return result(prepared, null);
  }

  public AuthorizationResult getCompanyAutopay(Long companyId) {
    RecurringAuthorizationStore.Prepared prepared = store.findCompanyAutopay(companyId);
    if (prepared == null) {
      return null;
    }
    return result(prepared, null);
  }

  public AuthorizationResult verifyCompanyAutopay(
      Long companyId, String paymentId, String orderId, String signature) {
    RecurringAuthorizationStore.Prepared prepared = store.findCompanyAutopay(companyId);
    if (prepared == null) {
      return null;
    }
    if ("ACTIVE".equalsIgnoreCase(prepared.status())) {
      return result(prepared, null);
    }
    try {
      if (orderId == null || !orderId.equals(prepared.subscriptionSessionId())) {
        throw new ConflictException("Mandate checkout order does not match the company");
      }
      String customerId = prepared.customerId();
      String tokenId = gateway.verifyMandateToken(orderId, paymentId, signature, customerId);
      if (tokenId == null || !tokenId.startsWith("token_")) {
        throw new ConflictException("A verified recurring token is required");
      }
      prepared = store.activateCompanyAutopay(prepared.id(), tokenId, paymentId, customerId);
      return result(prepared, null);
    } catch (RuntimeException exception) {
      store.markAmbiguous(prepared.id());
      throw exception;
    }
  }

  public AuthorizationResult verifyAndRefreshCompanyAutopay(Long companyId) {
    RecurringAuthorizationStore.Prepared prepared = store.findCompanyAutopay(companyId);
    if (prepared == null) {
      return null;
    }
    // No signed checkout evidence was supplied. Never ask Razorpay to fetch the local
    // UDS-AP mandate intent as a token, or convert a paid order into ACTIVE status.
    if (prepared.providerSubscriptionId() == null
        || !prepared.providerSubscriptionId().startsWith("token_")) {
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

  public boolean cancelCompanyAutopay(Long companyId) {
    RecurringAuthorizationStore.Prepared active = store.findActiveCompanyAutopay(companyId);
    if (active != null) {
      gateway.cancelRecurringAuthorization(active.providerSubscriptionId(), active.customerId());
    }
    return store.cancelCompanyAutopay(companyId);
  }

  public void bindInitialActivation(Long companyId, Long checkoutIntentId, Long subscriptionId) {
    store.bindInitialActivation(companyId, checkoutIntentId, subscriptionId);
  }

  public void bindPayment(long authorizationId, Long paymentId) {
    store.bindPayment(authorizationId, paymentId);
  }

  private boolean hasProviderCheckout(RecurringAuthorizationStore.Prepared prepared) {
    return prepared.subscriptionSessionId() != null
        && prepared.subscriptionSessionId().startsWith("order_")
        && prepared.customerId() != null
        && prepared.customerId().startsWith("cust_");
  }

  private AuthorizationResult resolve(
      RecurringAuthorizationStore.Prepared prepared, PaymentProviderGateway.Customer customer) {
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
      if (prepared.subscriptionSessionId() == null || prepared.subscriptionSessionId().isBlank()) {
        throw new ConflictException(
            "Recurring authorization has no provider checkout continuation");
      }

      if (prepared.providerSubscriptionId() != null
          && prepared.providerSubscriptionId().startsWith("UDS-AP-")) {
        return result(prepared, remoteSubscription.providerSubscriptionReference());
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
      RecurringAuthorizationStore.Prepared prepared, PaymentProviderGateway.Customer customer) {
    if (prepared.newlyCreated()
        || ("CREATE_PENDING".equals(prepared.status())
            && prepared.providerSubscriptionId() != null
            && prepared.providerSubscriptionId().startsWith("UDS-AP-"))) {
      return createSubscription(prepared, customer);
    }
    try {
      return gateway.fetchRecurringAuthorization(prepared.providerSubscriptionId());
    } catch (RestClientResponseException exception) {
      if (!isAbsent(exception, "subscription_not_found")) {
        throw exception;
      }
      return createSubscription(prepared, customer);
    }
  }

  private PaymentProviderGateway.RecurringAuthorization createSubscription(
      RecurringAuthorizationStore.Prepared prepared, PaymentProviderGateway.Customer customer) {
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
      if (!isAbsent(exception, "payment_id_not_found", "payment_not_found")) {
        throw exception;
      }
      return submitAuthorization(prepared);
    }
  }

  private boolean isAbsent(RestClientResponseException exception, String... errorCodes) {
    if (exception.getStatusCode().value() == 404) {
      return true;
    }
    if (exception.getStatusCode().value() == 400) {
      String body = exception.getResponseBodyAsString();
      if (body != null) {
        for (String code : errorCodes) {
          if (body.contains(code)) {
            return true;
          }
        }
      }
    }
    return false;
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
        actionUrl,
        prepared.customerId());
  }

  public record AuthorizationResult(
      long authorizationId,
      String providerSubscriptionId,
      String subscriptionSessionId,
      String status,
      String actionUrl,
      String customerId) {
    public AuthorizationResult(
        long authorizationId,
        String providerSubscriptionId,
        String subscriptionSessionId,
        String status,
        String actionUrl) {
      this(authorizationId, providerSubscriptionId, subscriptionSessionId, status, actionUrl, null);
    }
  }
}
