package com.messaging.billing.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class RecurringAuthorizationServiceTests {

  @Test
  void providerAuthorizationUsesStableSubscriptionAndAuthPaymentIdentity() {
    RecurringAuthorizationStore store = mock(RecurringAuthorizationStore.class);
    PaymentProviderGateway gateway = mock(PaymentProviderGateway.class);
    CashfreeProperties properties = new CashfreeProperties();
    properties.setReturnUrl("https://app.unifydesk.in/billing/return");
    RecurringAuthorizationService service =
        new RecurringAuthorizationService(store, gateway, properties);

    var initial = prepared(90L, 77L, null, "UDS-77", null, null, "CREATE_PENDING", new BigDecimal("999.00"), true);
    var withSession =
        prepared(90L, 77L, null, "UDS-77", "subs-session", null, "PENDING_AUTH", new BigDecimal("999.00"), false);
    var active =
        prepared(90L, 77L, null, "UDS-77", "subs-session", "cf-auth-1", "ACTIVE", new BigDecimal("999.00"), false);

    when(store.prepare(10L, 77L, new BigDecimal("999.00"), "INR")).thenReturn(initial);
    when(gateway.createRecurringAuthorization(
            eq("UDS-77"),
            eq(new BigDecimal("999.00")),
            eq("INR"),
            any(),
            anyString(),
            anyString()))
        .thenReturn(
            new PaymentProviderGateway.RecurringAuthorization(
                "UDS-77", "subs-session", "PENDING", "cf-sub-1"));
    when(store.applySubscription(eq(90L), any())).thenReturn(withSession);
    when(gateway.fetchSubscriptionPayment("UDS-77", "UDA-90"))
        .thenThrow(new org.springframework.web.client.HttpClientErrorException(
            org.springframework.http.HttpStatus.NOT_FOUND));
    when(gateway.authorizeRecurringSubscription(
            eq("UDS-77"),
            eq("subs-session"),
            eq("UDA-90"),
            eq(PaymentProviderGateway.AuthorizationMethod.HOSTED_UPI_LINK),
            anyString()))
        .thenReturn(
            new PaymentProviderGateway.RemoteSubscriptionPayment(
                "UDA-90",
                "cf-auth-1",
                "SUCCESS",
                BigDecimal.ONE,
                "INR",
                null,
                "https://cashfree.example/auth",
                null));
    when(store.applyAuthorizationPayment(eq(90L), any())).thenReturn(active);

    var result =
        service.prepareOrResolve(
            10L,
            77L,
            new BigDecimal("999.00"),
            "INR",
            new PaymentProviderGateway.Customer(
                "company-10", "Acme", "billing@example.com", "9999999999"));

    assertEquals("UDS-77", result.providerSubscriptionId());
    assertEquals("ACTIVE", result.status());
    verify(gateway, times(1))
        .createRecurringAuthorization(anyString(), any(), anyString(), any(), anyString(), anyString());
    verify(gateway, times(1))
        .authorizeRecurringSubscription(
            eq("UDS-77"),
            eq("subs-session"),
            eq("UDA-90"),
            any(),
            anyString());
  }

  @Test
  void initialCheckoutBootstrapIsStableAndReplayDoesNotCreateSecondProviderSubscription() {
    RecurringAuthorizationStore store = mock(RecurringAuthorizationStore.class);
    PaymentProviderGateway gateway = mock(PaymentProviderGateway.class);
    CashfreeProperties properties = new CashfreeProperties();
    RecurringAuthorizationService service =
        new RecurringAuthorizationService(store, gateway, properties);

    var initial =
        prepared(91L, null, 555L, "UDS-I-555", null, null, "CREATE_PENDING", new BigDecimal("499.00"), true);
    var active =
        prepared(91L, null, 555L, "UDS-I-555", "subs-session", "cf-auth-2", "ACTIVE", new BigDecimal("499.00"), false);
    when(store.prepareInitial(10L, 555L, new BigDecimal("499.00"), "INR"))
        .thenReturn(initial)
        .thenReturn(active);
    when(gateway.createRecurringAuthorization(
            eq("UDS-I-555"),
            eq(new BigDecimal("499.00")),
            eq("INR"),
            any(),
            anyString(),
            anyString()))
        .thenReturn(
            new PaymentProviderGateway.RecurringAuthorization(
                "UDS-I-555", "subs-session", "ACTIVE", "cf-sub-2"));
    when(store.applySubscription(eq(91L), any())).thenReturn(active);

    var customer =
        new PaymentProviderGateway.Customer(
            "company-10", "Acme", "billing@example.com", "9999999999");
    var first =
        service.prepareForInitialCheckout(
            10L, 555L, new BigDecimal("499.00"), "INR", customer);
    var replay =
        service.prepareForInitialCheckout(
            10L, 555L, new BigDecimal("499.00"), "INR", customer);

    assertEquals("UDS-I-555", first.providerSubscriptionId());
    assertEquals("UDS-I-555", replay.providerSubscriptionId());
    verify(gateway, times(1))
        .createRecurringAuthorization(
            eq("UDS-I-555"), any(), eq("INR"), eq(customer), anyString(), anyString());
  }

  private RecurringAuthorizationStore.Prepared prepared(
      long id,
      Long subscriptionId,
      Long checkoutIntentId,
      String providerSubscriptionId,
      String sessionId,
      String providerAuthorizationPaymentId,
      String status,
      BigDecimal maxAmount,
      boolean newlyCreated) {
    return new RecurringAuthorizationStore.Prepared(
        id,
        10L,
        subscriptionId,
        checkoutIntentId,
        providerSubscriptionId,
        sessionId,
        "UDA-" + id,
        providerAuthorizationPaymentId,
        status,
        maxAmount,
        "INR",
        newlyCreated);
  }
}
