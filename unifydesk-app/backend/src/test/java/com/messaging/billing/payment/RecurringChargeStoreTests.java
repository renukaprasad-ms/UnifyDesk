package com.messaging.billing.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

import com.messaging.billing.entity.Invoice;
import com.messaging.billing.entity.Payment;
import com.messaging.billing.entity.PaymentAttempt;
import com.messaging.billing.enums.InvoiceStatus;
import com.messaging.billing.enums.PaymentAttemptStatus;
import com.messaging.billing.enums.PaymentPurpose;
import com.messaging.billing.enums.PaymentStatus;
import com.messaging.billing.repository.PaymentAttemptRepository;
import com.messaging.billing.repository.PaymentRepository;
import com.messaging.company.entity.Company;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RecurringChargeStoreTests {

  @Test
  void httpResponseAfterWebhookSuccessBindsSameCommandAttemptWithoutCreatingAnotherAttempt() {
    PaymentRepository payments = mock(PaymentRepository.class);
    PaymentAttemptRepository attempts = mock(PaymentAttemptRepository.class);
    RecurringChargeStore store = new RecurringChargeStore(payments, attempts);

    Payment payment = renewalPayment();
    PaymentAttempt attempt = recurringAttempt(payment);
    attempt.setStatus(PaymentAttemptStatus.SUCCEEDED);
    attempt.setProviderPaymentId("cf-pay-1");
    attempt.setProviderCreatedAt(Instant.parse("2026-10-10T00:00:00Z"));

    when(attempts.findByIdForUpdate(41L)).thenReturn(Optional.of(attempt));
    when(payments.findByIdForUpdate(30L)).thenReturn(Optional.of(payment));
    when(attempts.findByProviderPaymentId("cf-pay-1")).thenReturn(Optional.of(attempt));
    when(attempts.saveAndFlush(attempt)).thenReturn(attempt);

    var remote =
        new PaymentProviderGateway.RemoteSubscriptionPayment(
            "UDC-41",
            "cf-pay-1",
            "SUCCESS",
            new BigDecimal("499.00"),
            "INR",
            Instant.parse("2026-10-10T00:00:00Z"),
            null,
            null);

    var bound = store.bindRemote(41L, remote);

    assertEquals(41L, bound.attemptId());
    assertEquals("UDC-41", bound.providerCommandId());
    assertEquals("cf-pay-1", bound.providerPaymentId());
    assertEquals(PaymentAttemptStatus.SUCCEEDED, attempt.getStatus());
    verify(attempts, never()).save(argThat(other -> other != attempt));
  }

  private Payment renewalPayment() {
    Company company = new Company();
    company.setId(10L);
    Invoice invoice = new Invoice();
    invoice.setId(20L);
    invoice.setCompany(company);
    invoice.setStatus(InvoiceStatus.OPEN);
    invoice.setAmountDue(new BigDecimal("499.00"));
    invoice.setCurrency("INR");

    Payment payment = new Payment();
    payment.setId(30L);
    payment.setCompany(company);
    payment.setInvoice(invoice);
    payment.setPurpose(PaymentPurpose.RENEWAL);
    payment.setSubscriptionId(77L);
    payment.setProviderSubscriptionId("UDS-77");
    payment.setStatus(PaymentStatus.PENDING);
    payment.setAmount(new BigDecimal("499.00"));
    payment.setCurrency("INR");
    return payment;
  }

  private PaymentAttempt recurringAttempt(Payment payment) {
    PaymentAttempt attempt = new PaymentAttempt();
    attempt.setId(41L);
    attempt.setPayment(payment);
    attempt.setAttemptNo(1);
    attempt.setProviderCommandId("UDC-41");
    attempt.setProviderSubmissionState("CONFIRMED");
    attempt.setStatus(PaymentAttemptStatus.PENDING);
    attempt.setAmount(new BigDecimal("499.00"));
    attempt.setCurrency("INR");
    return attempt;
  }
}
