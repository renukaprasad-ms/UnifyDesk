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
    RazorpayProperties properties = new RazorpayProperties();
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
    RazorpayProperties properties = new RazorpayProperties();
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


  @Test
  void mandateVerificationRejectsOrderFromAnotherCompanyWithoutCallingProvider() {
    RecurringAuthorizationStore store = mock(RecurringAuthorizationStore.class);
    PaymentProviderGateway gateway = mock(PaymentProviderGateway.class);
    RecurringAuthorizationService service =
        new RecurringAuthorizationService(store, gateway, new RazorpayProperties());
    when(store.findCompanyAutopay(10L)).thenReturn(
        prepared(123L, null, null, "UDS-AP-10", "order_expected", null,
            "PENDING_AUTH", new BigDecimal("15000.00"), false));

    org.junit.jupiter.api.Assertions.assertThrows(
        com.messaging.common.exception.ConflictException.class,
        () -> service.verifyCompanyAutopay(
            10L, "pay_from_other_company", "order_other", "fake-signature"));

    verify(gateway, never()).verifyMandateToken(any(), any(), any(), any());
    verify(store, never()).activateCompanyAutopay(anyLong(), any(), any(), any());
  }

  @Test
  void mandateVerificationRejectsMissingConfirmedToken() {
    RecurringAuthorizationStore store = mock(RecurringAuthorizationStore.class);
    PaymentProviderGateway gateway = mock(PaymentProviderGateway.class);
    RecurringAuthorizationService service =
        new RecurringAuthorizationService(store, gateway, new RazorpayProperties());
    when(store.findCompanyAutopay(10L)).thenReturn(
        prepared(124L, null, null, "UDS-AP-10", "order_expected", null,
            "PENDING_AUTH", new BigDecimal("15000.00"), false));

    org.junit.jupiter.api.Assertions.assertThrows(
        com.messaging.common.exception.ConflictException.class,
        () -> service.verifyCompanyAutopay(
            10L, "pay_without_token", "order_expected", "fake-signature"));

    verify(gateway).verifyMandateToken(
        eq("order_expected"), eq("pay_without_token"), eq("fake-signature"), any());
    verify(store, never()).activateCompanyAutopay(anyLong(), any(), any(), any());
  }


  @Test
  void companyAutopayCreatesRealAuthorizationOrderAndReturnsRazorpayCustomer() {
    RecurringAuthorizationStore store = mock(RecurringAuthorizationStore.class);
    PaymentProviderGateway gateway = mock(PaymentProviderGateway.class);
    RazorpayProperties properties = new RazorpayProperties();
    properties.setRecurringEnabled(true);
    RecurringAuthorizationService service =
        new RecurringAuthorizationService(store, gateway, properties);
    BigDecimal amount = new BigDecimal("15000.00");
    var customer = new PaymentProviderGateway.Customer(
        "company-10", "Example", "billing@example.com", "9876543210");
    var initial = new RecurringAuthorizationStore.Prepared(
        201L, 10L, null, null, "UDS-AP-10-201", null, "UDA-201", null,
        "CREATE_PENDING", amount, "INR", true, null);
    var persisted = new RecurringAuthorizationStore.Prepared(
        201L, 10L, null, null, "UDS-AP-10-201", "order_123", "UDA-201", null,
        "PENDING_AUTH", amount, "INR", false, "cust_123");
    when(store.prepareCompanyAutopay(10L, amount, "INR")).thenReturn(initial);
    when(gateway.createRecurringAuthorization(
        eq("UDS-AP-10-201"), eq(amount), eq("INR"),
        eq(customer), anyString(), anyString()))
        .thenReturn(new PaymentProviderGateway.RecurringAuthorization(
            "UDS-AP-10-201", "order_123", "PENDING_AUTH", "cust_123"));
    when(store.applySubscription(eq(201L), any())).thenReturn(persisted);

    var result = service.prepareCompanyAutopay(10L, amount, "INR", customer);

    assertEquals("order_123", result.subscriptionSessionId());
    assertEquals("cust_123", result.customerId());
    assertEquals("PENDING_AUTH", result.status());
    verify(gateway, times(1)).createRecurringAuthorization(
        anyString(), any(), anyString(), any(), anyString(), anyString());
  }

  @Test
  void companyAutopayReusesPendingProviderOrderWithoutCreatingANewOne() {
    RecurringAuthorizationStore store = mock(RecurringAuthorizationStore.class);
    PaymentProviderGateway gateway = mock(PaymentProviderGateway.class);
    RazorpayProperties properties = new RazorpayProperties();
    properties.setRecurringEnabled(true);
    RecurringAuthorizationService service =
        new RecurringAuthorizationService(store, gateway, properties);
    var pending = new RecurringAuthorizationStore.Prepared(
        202L, 10L, null, null, "UDS-AP-10-202", "order_existing", "UDA-202", null,
        "PENDING_AUTH", new BigDecimal("15000.00"), "INR", false, "cust_existing");
    when(store.prepareCompanyAutopay(eq(10L), any(), eq("INR"))).thenReturn(pending);

    var result = service.prepareCompanyAutopay(10L, null, "INR", null);

    assertEquals("order_existing", result.subscriptionSessionId());
    assertEquals("cust_existing", result.customerId());
    verifyNoInteractions(gateway);
  }

  @Test
  void companyAutopayRejectsIncompleteLocalReferenceInsteadOfReturningFakeOrder() {
    RecurringAuthorizationStore store = mock(RecurringAuthorizationStore.class);
    PaymentProviderGateway gateway = mock(PaymentProviderGateway.class);
    RazorpayProperties properties = new RazorpayProperties();
    properties.setRecurringEnabled(true);
    RecurringAuthorizationService service =
        new RecurringAuthorizationService(store, gateway, properties);
    var corrupted = new RecurringAuthorizationStore.Prepared(
        203L, 10L, null, null, "UDS-AP-10-203", "UDS-AP-10-203", "UDA-203", null,
        "PENDING_AUTH", new BigDecimal("15000.00"), "INR", false, null);
    when(store.prepareCompanyAutopay(eq(10L), any(), eq("INR"))).thenReturn(corrupted);

    org.junit.jupiter.api.Assertions.assertThrows(
        com.messaging.common.exception.ConflictException.class,
        () -> service.prepareCompanyAutopay(10L, null, "INR", null));
    verifyNoInteractions(gateway);
  }

  @Test
  void unsignedMandateRefreshNeverTreatsLocalReferenceAsProviderToken() {
    RecurringAuthorizationStore store = mock(RecurringAuthorizationStore.class);
    PaymentProviderGateway gateway = mock(PaymentProviderGateway.class);
    RecurringAuthorizationService service =
        new RecurringAuthorizationService(store, gateway, new RazorpayProperties());
    var pending = new RecurringAuthorizationStore.Prepared(
        204L, 10L, null, null, "UDS-AP-10-204", "UDS-AP-10-204",
        "UDA-204", null, "PENDING_AUTH", new BigDecimal("15000.00"), "INR",
        false, null);
    when(store.findCompanyAutopay(10L)).thenReturn(pending);

    var result = service.verifyAndRefreshCompanyAutopay(10L);

    assertEquals("PENDING_AUTH", result.status());
    verifyNoInteractions(gateway);
    verify(store, never()).applySubscription(anyLong(), any());
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
