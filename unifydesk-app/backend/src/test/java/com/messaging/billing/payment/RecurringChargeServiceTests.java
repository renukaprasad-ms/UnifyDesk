package com.messaging.billing.payment;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.messaging.billing.enums.PaymentAttemptStatus;
import com.messaging.common.exception.ConflictException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class RecurringChargeServiceTests {

  private RecurringAuthorizationStore authorizations;
  private RecurringChargeStore charges;
  private PaymentProviderGateway gateway;
  private PaymentConfirmationService confirmations;
  private JdbcTemplate jdbc;
  private RecurringChargeService service;

  @BeforeEach
  void setUp() {
    authorizations = mock(RecurringAuthorizationStore.class);
    charges = mock(RecurringChargeStore.class);
    gateway = mock(PaymentProviderGateway.class);
    confirmations = mock(PaymentConfirmationService.class);
    jdbc = mock(JdbcTemplate.class);
    service = new RecurringChargeService(authorizations, charges, gateway, confirmations, jdbc);

    when(jdbc.queryForList(anyString(), eq(Long.class), eq(30L))).thenReturn(List.of(10L));
    when(jdbc.queryForObject(anyString(), eq(Long.class), eq(30L))).thenReturn(77L);
    when(authorizations.active(10L, 77L)).thenReturn(activeAuthorization());
  }

  @Test
  void recurringChargeUsesExactCanonicalPaymentAmount() {
    Instant scheduledAt = Instant.parse("2026-10-10T00:00:00Z");
    var prepared = prepared(true);
    when(charges.prepare(30L, "UDS-77")).thenReturn(prepared);
    when(gateway.chargeAuthorizedSubscription(
            eq("UDS-77"),
            eq("UDC-41"),
            eq(new BigDecimal("499.00")),
            eq(scheduledAt),
            anyString()))
        .thenReturn(remote("PENDING"));
    when(charges.bindRemote(eq(41L), any())).thenReturn(prepared);

    service.charge(30L, scheduledAt);

    verify(charges).assertCommerciallyCurrent(prepared, scheduledAt);
    verify(gateway)
        .chargeAuthorizedSubscription(
            eq("UDS-77"),
            eq("UDC-41"),
            eq(new BigDecimal("499.00")),
            eq(scheduledAt),
            anyString());
    verify(confirmations)
        .confirmRecurringAttempt(
            eq("UDC-41"),
            eq("UDS-77"),
            eq("cf-pay-1"),
            eq(new BigDecimal("499.00")),
            eq("INR"),
            eq(PaymentAttemptStatus.PENDING),
            isNull(),
            isNull());
  }

  @Test
  void staleCommercialStateNeverCallsCashfreeAndTerminalizesLocalCommand() {
    Instant scheduledAt = Instant.parse("2026-10-10T00:00:00Z");
    var prepared = prepared(true);
    when(charges.prepare(30L, "UDS-77")).thenReturn(prepared);
    doThrow(new ConflictException("stale commercial state"))
        .when(charges)
        .assertCommerciallyCurrent(prepared, scheduledAt);

    assertThrows(ConflictException.class, () -> service.charge(30L, scheduledAt));

    verify(charges).markCommercialConflict(41L);
    verifyNoInteractions(gateway);
    verifyNoInteractions(confirmations);
    verify(charges, never()).markAmbiguous(anyLong());
  }

  @Test
  void ambiguousRetryFetchesSameCommandInsteadOfCreatingDuplicateCharge() {
    Instant scheduledAt = Instant.parse("2026-10-10T00:00:00Z");
    when(charges.prepare(30L, "UDS-77"))
        .thenReturn(prepared(true))
        .thenReturn(prepared(false));
    when(gateway.chargeAuthorizedSubscription(anyString(), anyString(), any(), any(), anyString()))
        .thenThrow(new RuntimeException("timeout"));
    when(gateway.fetchSubscriptionPayment("UDS-77", "UDC-41"))
        .thenReturn(remote("SUCCESS"));
    when(charges.bindRemote(eq(41L), any())).thenReturn(prepared(false));

    try {
      service.charge(30L, scheduledAt);
    } catch (RuntimeException ignored) {
    }
    service.charge(30L, scheduledAt);

    verify(charges).markAmbiguous(41L);
    verify(gateway, times(1))
        .chargeAuthorizedSubscription(anyString(), anyString(), any(), any(), anyString());
    verify(gateway, times(1)).fetchSubscriptionPayment("UDS-77", "UDC-41");
    verify(confirmations)
        .confirmRecurringAttempt(
            eq("UDC-41"),
            eq("UDS-77"),
            eq("cf-pay-1"),
            eq(new BigDecimal("499.00")),
            eq("INR"),
            eq(PaymentAttemptStatus.SUCCEEDED),
            isNull(),
            isNull());
  }

  private RecurringAuthorizationStore.Prepared activeAuthorization() {
    return new RecurringAuthorizationStore.Prepared(
        90L,
        10L,
        77L,
        null,
        "UDS-77",
        "subs-session",
        "UDA-90",
        "cf-auth-1",
        "ACTIVE",
        new BigDecimal("999.00"),
        "INR",
        false);
  }

  private RecurringChargeStore.Prepared prepared(boolean newlyCreated) {
    return new RecurringChargeStore.Prepared(
        30L,
        41L,
        10L,
        77L,
        "UDS-77",
        null,
        "UDC-41",
        null,
        new BigDecimal("499.00"),
        "INR",
        newlyCreated,
        false);
  }

  private PaymentProviderGateway.RemoteSubscriptionPayment remote(String status) {
    return new PaymentProviderGateway.RemoteSubscriptionPayment(
        "UDC-41",
        "cf-pay-1",
        status,
        new BigDecimal("499.00"),
        "INR",
        null,
        null,
        null);
  }
}
