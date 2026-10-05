package com.messaging.billing.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.messaging.billing.enums.PaymentPurpose;
import com.messaging.billing.enums.PaymentStatus;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class PaymentCollectionServiceTests {

  @Test
  void durableIntentIsPreparedBeforeProviderCallAndSessionIsPersisted() {
    PaymentCollectionStore store = mock(PaymentCollectionStore.class);
    PaymentProviderGateway gateway = mock(PaymentProviderGateway.class);
    CashfreeProperties properties = properties();
    PaymentCollectionService service = new PaymentCollectionService(store, gateway, properties);

    var prepared = prepared(true, null, "CREATE_PENDING");
    when(store.prepare(
            eq(10L),
            eq(20L),
            eq(PaymentPurpose.INITIAL_SUBSCRIPTION),
            eq("checkout-1"),
            eq(40L),
            isNull(),
            isNull()))
        .thenReturn(prepared);
    when(gateway.createOrder(
            eq("UD-30"),
            eq(new BigDecimal("499.00")),
            eq("INR"),
            any(),
            anyString(),
            anyString(),
            anyString()))
        .thenReturn(new PaymentProviderGateway.Checkout("UD-30", "session-1", "ACTIVE"));
    when(store.acceptProviderOrder(eq(30L), any()))
        .thenReturn(prepared(false, "session-1", "CONFIRMED"));

    var customer =
        new PaymentProviderGateway.Customer(
            "company-10", "Acme", "billing@example.com", "9999999999");
    var result =
        service.createOrResolve(
            10L,
            20L,
            PaymentPurpose.INITIAL_SUBSCRIPTION,
            "checkout-1",
            40L,
            null,
            null,
            customer);

    assertEquals("session-1", result.paymentSessionId());
    InOrder order = inOrder(store, gateway);
    order.verify(store).prepare(anyLong(), anyLong(), any(), anyString(), any(), any(), any());
    order.verify(gateway)
        .createOrder(anyString(), any(), anyString(), any(), anyString(), anyString(), anyString());
    order.verify(store).acceptProviderOrder(eq(30L), any());
  }

  @Test
  void providerTimeoutPreservesAmbiguousIntentAndReplayDoesNotCreateSecondOrder() {
    PaymentCollectionStore store = mock(PaymentCollectionStore.class);
    PaymentProviderGateway gateway = mock(PaymentProviderGateway.class);
    PaymentCollectionService service =
        new PaymentCollectionService(store, gateway, properties());
    var customer =
        new PaymentProviderGateway.Customer(
            "company-10", "Acme", "billing@example.com", "9999999999");

    when(store.prepare(anyLong(), anyLong(), any(), anyString(), any(), any(), any()))
        .thenReturn(prepared(true, null, "CREATE_PENDING"))
        .thenReturn(prepared(false, null, "AMBIGUOUS"));
    when(gateway.createOrder(anyString(), any(), anyString(), any(), anyString(), anyString(), anyString()))
        .thenThrow(new RuntimeException("timeout"));
    when(gateway.fetchOrder("UD-30"))
        .thenReturn(new PaymentProviderGateway.Checkout("UD-30", "recovered-session", "ACTIVE"));
    when(store.acceptProviderOrder(eq(30L), any()))
        .thenReturn(prepared(false, "recovered-session", "CONFIRMED"));

    assertThrows(
        RuntimeException.class,
        () ->
            service.createOrResolve(
                10L, 20L, PaymentPurpose.RENEWAL, "k", null, null, 50L, customer));

    var replay =
        service.createOrResolve(
            10L, 20L, PaymentPurpose.RENEWAL, "k", null, null, 50L, customer);

    assertEquals("recovered-session", replay.paymentSessionId());
    verify(store).markProviderOrderAmbiguous(30L);
    verify(gateway, times(1))
        .createOrder(anyString(), any(), anyString(), any(), anyString(), anyString(), anyString());
    verify(gateway, times(1)).fetchOrder("UD-30");
  }

  @Test
  void exactReplayWithPersistedSessionDoesNotCallProvider() {
    PaymentCollectionStore store = mock(PaymentCollectionStore.class);
    PaymentProviderGateway gateway = mock(PaymentProviderGateway.class);
    PaymentCollectionService service =
        new PaymentCollectionService(store, gateway, properties());
    when(store.prepare(anyLong(), anyLong(), any(), anyString(), any(), any(), any()))
        .thenReturn(prepared(false, "same-session", "CONFIRMED"));

    var result =
        service.createOrResolve(
            10L,
            20L,
            PaymentPurpose.RENEWAL,
            "k",
            null,
            null,
            50L,
            new PaymentProviderGateway.Customer("c", "Acme", "x@y.com", "9999999999"));

    assertEquals("same-session", result.paymentSessionId());
    verifyNoInteractions(gateway);
  }

  private CashfreeProperties properties() {
    CashfreeProperties properties = new CashfreeProperties();
    properties.setReturnUrl("https://app.unifydesk.in/return");
    properties.setNotifyUrl("https://api.unifydesk.in/api/v1/webhooks/cashfree");
    return properties;
  }

  private PaymentCollectionStore.Prepared prepared(
      boolean newlyCreated, String session, String providerState) {
    return new PaymentCollectionStore.Prepared(
        30L,
        20L,
        "UD-30",
        session,
        new BigDecimal("499.00"),
        "INR",
        PaymentStatus.PENDING,
        providerState,
        newlyCreated);
  }
}
