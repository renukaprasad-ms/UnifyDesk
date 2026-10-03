package com.messaging.billing.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.messaging.billing.entity.CompanySubscription;
import com.messaging.billing.entity.Invoice;
import com.messaging.billing.entity.Payment;
import com.messaging.billing.entity.PaymentAttempt;
import com.messaging.billing.enums.PaymentAttemptStatus;
import com.messaging.billing.enums.PaymentPurpose;
import com.messaging.billing.enums.PaymentStatus;
import com.messaging.billing.enums.SubscriptionStatus;
import com.messaging.billing.repository.PaymentAttemptRepository;
import com.messaging.billing.repository.PaymentRepository;
import com.messaging.billing.service.InvoiceService;
import com.messaging.billing.service.SubscriptionChangeService;
import com.messaging.billing.service.SubscriptionCheckoutService;
import com.messaging.billing.service.SubscriptionLifecycleService;
import com.messaging.common.exception.ConflictException;
import com.messaging.company.entity.Company;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class PaymentConfirmationServiceTests {

  private PaymentRepository payments;
  private PaymentAttemptRepository attempts;
  private InvoiceService invoices;
  private SubscriptionCheckoutService checkout;
  private SubscriptionChangeService changes;
  private SubscriptionLifecycleService lifecycle;
  private JdbcTemplate jdbc;
  private PaymentConfirmationService service;

  @BeforeEach
  void setUp() {
    payments = mock(PaymentRepository.class);
    attempts = mock(PaymentAttemptRepository.class);
    invoices = mock(InvoiceService.class);
    checkout = mock(SubscriptionCheckoutService.class);
    changes = mock(SubscriptionChangeService.class);
    lifecycle = mock(SubscriptionLifecycleService.class);
    jdbc = mock(JdbcTemplate.class);
    service =
        new PaymentConfirmationService(
            payments, attempts, invoices, checkout, changes, lifecycle, jdbc);
    when(attempts.findByPaymentIdOrderByAttemptNoAsc(anyLong())).thenReturn(List.of());
    when(attempts.saveAndFlush(any(PaymentAttempt.class))).thenAnswer(i -> i.getArgument(0));
  }

  @Test
  void amountMismatchCannotMarkInvoicePaid() {
    Payment payment = payment(PaymentStatus.PENDING, PaymentPurpose.UPGRADE);
    when(payments.findByProviderAndProviderOrderId("CASHFREE", "UD-1"))
        .thenReturn(Optional.of(payment));
    when(payments.findByIdForUpdate(1L)).thenReturn(Optional.of(payment));

    assertThrows(
        ConflictException.class,
        () ->
            service.confirmSuccess(
                "UD-1",
                "cf-1",
                new BigDecimal("999.00"),
                "INR",
                Instant.parse("2026-10-03T00:00:00Z")));

    verify(invoices, never()).markPaidConfirmed(anyLong(), any());
  }

  @Test
  void duplicateSuccessWithAppliedLifecycleDoesNotApplyUpgradeTwice() {
    Payment payment = payment(PaymentStatus.SUCCEEDED, PaymentPurpose.UPGRADE);
    payment.setSubscriptionChangeId(88L);
    payment.setLifecycleAppliedAt(Instant.parse("2026-10-03T00:00:00Z"));
    when(payments.findByProviderAndProviderOrderId("CASHFREE", "UD-1"))
        .thenReturn(Optional.of(payment));
    when(payments.findByIdForUpdate(1L)).thenReturn(Optional.of(payment));
    when(attempts.findByProviderPaymentId("cf-1")).thenReturn(Optional.empty());

    service.confirmSuccess(
        "UD-1",
        "cf-1",
        new BigDecimal("1000.00"),
        "INR",
        Instant.parse("2026-10-03T00:00:00Z"));

    verify(invoices, never()).markPaidConfirmed(anyLong(), any());
    verify(changes, never()).applyConfirmedUpgrade(anyLong(), anyLong(), any());
  }

  @Test
  void earlyRenewalFailureLeavesActiveSubscriptionUntouched() {
    Instant boundary = Instant.parse("2026-10-10T00:00:00Z");
    Payment payment = renewal(PaymentStatus.PENDING, boundary);
    when(payments.findByIdForUpdate(1L)).thenReturn(Optional.of(payment));

    service.recordFailureByPaymentId(
        1L,
        "cf-failed-1",
        payment.getAmount(),
        "INR",
        "DECLINED",
        PaymentAttemptStatus.FAILED,
        Instant.parse("2026-10-09T00:00:00Z"));

    assertEquals(PaymentStatus.FAILED, payment.getStatus());
    verify(lifecycle, never()).markPastDue(anyLong(), any());
    verifyNoInteractions(jdbc);
  }

  @Test
  void dueRenewalFailureMarksPastDueAndSchedulesDunning() {
    Instant boundary = Instant.parse("2026-10-10T00:00:00Z");
    Payment payment = renewal(PaymentStatus.PENDING, boundary);
    CompanySubscription subscription = new CompanySubscription();
    subscription.setStatus(SubscriptionStatus.ACTIVE);
    when(payments.findByIdForUpdate(1L)).thenReturn(Optional.of(payment));
    when(lifecycle.require(77L)).thenReturn(subscription);

    service.recordFailureByPaymentId(
        1L,
        "cf-failed-1",
        payment.getAmount(),
        "INR",
        "DECLINED",
        PaymentAttemptStatus.FAILED,
        boundary);

    verify(lifecycle).markPastDue(77L, boundary);
    verify(jdbc).update(contains("payment_dunning_jobs"), any(Object[].class));
  }

  @Test
  void providerConfirmedPreDueRenewalDoesNotAdvanceSubscriptionEarly() {
    Instant boundary = Instant.parse("2026-10-10T00:00:00Z");
    Payment payment = renewal(PaymentStatus.PENDING, boundary);
    when(payments.findByIdForUpdate(1L)).thenReturn(Optional.of(payment));
    when(attempts.findByProviderPaymentId("cf-success-1")).thenReturn(Optional.empty());

    service.confirmSuccessByPaymentId(
        1L,
        "cf-success-1",
        payment.getAmount(),
        "INR",
        Instant.parse("2026-10-09T12:00:00Z"));

    assertEquals(PaymentStatus.SUCCEEDED, payment.getStatus());
    verify(invoices).markPaidConfirmed(20L, Instant.parse("2026-10-09T12:00:00Z"));
    verify(lifecycle, never()).renewAfterConfirmedCollection(anyLong(), anyLong(), any());
  }

  @Test
  void confirmedRenewalAppliesThroughB1OnlyAtBoundary() {
    Instant boundary = Instant.parse("2026-10-10T00:00:00Z");
    Payment payment = renewal(PaymentStatus.SUCCEEDED, boundary);
    payment.setLifecycleAppliedAt(null);
    when(payments.findByIdForUpdate(1L)).thenReturn(Optional.of(payment));

    service.applyDeferredRenewal(1L, Instant.parse("2026-10-10T00:00:01Z"));

    verify(lifecycle)
        .renewAfterConfirmedCollection(77L, 20L, Instant.parse("2026-10-10T00:00:01Z"));
  }

  private Payment renewal(PaymentStatus status, Instant boundary) {
    Payment payment = payment(status, PaymentPurpose.RENEWAL);
    payment.setSubscriptionId(77L);
    payment.getInvoice().setServicePeriodStart(boundary);
    return payment;
  }

  private Payment payment(PaymentStatus status, PaymentPurpose purpose) {
    Company company = new Company();
    company.setId(10L);
    Invoice invoice = new Invoice();
    invoice.setId(20L);
    invoice.setCompany(company);

    Payment payment = new Payment();
    payment.setId(1L);
    payment.setCompany(company);
    payment.setInvoice(invoice);
    payment.setPurpose(purpose);
    payment.setStatus(status);
    payment.setAmount(new BigDecimal("1000.00"));
    payment.setCurrency("INR");
    payment.setProvider("CASHFREE");
    payment.setProviderOrderId("UD-1");
    return payment;
  }
}
