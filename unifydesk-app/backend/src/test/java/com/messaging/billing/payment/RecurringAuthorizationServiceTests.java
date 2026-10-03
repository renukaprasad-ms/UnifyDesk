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

    var initial =
        new RecurringAuthorizationStore.Prepared(
            90L,
            10L,
            77L,
            "UDS-77",
            null,
            "UDA-90",
            "CREATE_PENDING",
            new BigDecimal("999.00"),
            "INR",
            true);
    var withSession =
        new RecurringAuthorizationStore.Prepared(
            90L,
            10L,
            77L,
            "UDS-77",
            "subs-session",
            "UDA-90",
            "PENDING_AUTH",
            new BigDecimal("999.00"),
            "INR",
            false);
    var active =
        new RecurringAuthorizationStore.Prepared(
            90L,
            10L,
            77L,
            "UDS-77",
            "subs-session",
            "UDA-90",
            "ACTIVE",
            new BigDecimal("999.00"),
            "INR",
            false);

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
}
