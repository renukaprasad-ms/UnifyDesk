package com.messaging.billing.payment;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.messaging.billing.enums.RefundStatus;
import com.messaging.billing.repository.RefundRepository;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class RefundServiceTests {

  @Test
  void durableRefundExistsBeforeProviderCall() {
    RefundStore store = mock(RefundStore.class);
    RefundRepository refunds = mock(RefundRepository.class);
    PaymentProviderGateway gateway = mock(PaymentProviderGateway.class);
    RefundService service = new RefundService(store, refunds, gateway);

    var prepared = prepared(true, RefundStatus.CREATED, "CREATE_PENDING");
    when(store.prepare(10L, 20L, new BigDecimal("100.00"), "refund-1"))
        .thenReturn(prepared);
    when(gateway.createRefund(
            eq("UD-30"),
            eq("UDR-40"),
            eq(new BigDecimal("100.00")),
            any(),
            anyString()))
        .thenReturn(remote("PENDING"));

    assertThrows(
        java.util.NoSuchElementException.class,
        () -> service.refund(10L, 20L, new BigDecimal("100.00"), "refund-1", "partial"));

    InOrder order = inOrder(store, gateway);
    order.verify(store).prepare(10L, 20L, new BigDecimal("100.00"), "refund-1");
    order.verify(gateway)
        .createRefund(eq("UD-30"), eq("UDR-40"), eq(new BigDecimal("100.00")), any(), anyString());
    order.verify(store).applyRemote(40L, remote("PENDING"));
  }

  @Test
  void timeoutKeepsStableRefundIdentityAndReplayDoesNotCreateDuplicate() {
    RefundStore store = mock(RefundStore.class);
    RefundRepository refunds = mock(RefundRepository.class);
    PaymentProviderGateway gateway = mock(PaymentProviderGateway.class);
    RefundService service = new RefundService(store, refunds, gateway);

    when(store.prepare(10L, 20L, new BigDecimal("100.00"), "refund-1"))
        .thenReturn(prepared(true, RefundStatus.CREATED, "CREATE_PENDING"))
        .thenReturn(prepared(false, RefundStatus.PENDING, "AMBIGUOUS"));
    when(gateway.createRefund(anyString(), anyString(), any(), any(), anyString()))
        .thenThrow(new RuntimeException("timeout"));
    when(gateway.fetchRefund("UD-30", "UDR-40")).thenReturn(remote("SUCCESS"));

    assertThrows(
        RuntimeException.class,
        () -> service.refund(10L, 20L, new BigDecimal("100.00"), "refund-1", "partial"));

    assertThrows(
        java.util.NoSuchElementException.class,
        () -> service.refund(10L, 20L, new BigDecimal("100.00"), "refund-1", "partial"));

    verify(store).markAmbiguous(40L);
    verify(gateway, times(1))
        .createRefund(anyString(), anyString(), any(), any(), anyString());
    verify(gateway).fetchRefund("UD-30", "UDR-40");
    verify(store).applyRemote(40L, remote("SUCCESS"));
  }

  private RefundStore.Prepared prepared(
      boolean newlyCreated, RefundStatus status, String operationState) {
    return new RefundStore.Prepared(
        40L,
        10L,
        20L,
        "UD-30",
        "UDR-40",
        operationState,
        status,
        new BigDecimal("100.00"),
        "INR",
        newlyCreated);
  }

  private PaymentProviderGateway.RemoteRefund remote(String status) {
    return new PaymentProviderGateway.RemoteRefund(
        "UDR-40", "cf-refund-1", status, new BigDecimal("100.00"), "INR", null, null);
  }
}
