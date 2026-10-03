package com.messaging.billing.payment;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.messaging.billing.entity.Invoice;
import com.messaging.billing.entity.Payment;
import com.messaging.billing.enums.PaymentPurpose;
import com.messaging.billing.enums.PaymentStatus;
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
  }

  @Test
  void amountMismatchCannotMarkInvoicePaid() {
    Payment payment = payment(PaymentStatus.PENDING);
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
  void duplicateSuccessDoesNotApplyUpgradeTwice() {
    Payment payment = payment(PaymentStatus.SUCCEEDED);
    payment.setPurpose(PaymentPurpose.UPGRADE);
    payment.setSubscriptionChangeId(88L);
    when(payments.findByProviderAndProviderOrderId("CASHFREE", "UD-1"))
        .thenReturn(Optional.of(payment));
    when(payments.findByIdForUpdate(1L)).thenReturn(Optional.of(payment));
    when(attempts.findByProviderPaymentId("cf-1")).thenReturn(Optional.empty());
    when(attempts.findByPaymentIdOrderByAttemptNoAsc(1L)).thenReturn(List.of());
    when(attempts.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));

    service.confirmSuccess(
        "UD-1",
        "cf-1",
        new BigDecimal("1000.00"),
        "INR",
        Instant.parse("2026-10-03T00:00:00Z"));

    verify(invoices, never()).markPaidConfirmed(anyLong(), any());
    verify(changes, never()).applyConfirmedUpgrade(anyLong(), anyLong(), any());
  }

  private Payment payment(PaymentStatus status) {
    Company company = new Company();
    company.setId(10L);
    Invoice invoice = new Invoice();
    invoice.setId(20L);
    invoice.setCompany(company);

    Payment payment = new Payment();
    payment.setId(1L);
    payment.setCompany(company);
    payment.setInvoice(invoice);
    payment.setPurpose(PaymentPurpose.UPGRADE);
    payment.setStatus(status);
    payment.setAmount(new BigDecimal("1000.00"));
    payment.setCurrency("INR");
    payment.setProvider("CASHFREE");
    payment.setProviderOrderId("UD-1");
    return payment;
  }
}
