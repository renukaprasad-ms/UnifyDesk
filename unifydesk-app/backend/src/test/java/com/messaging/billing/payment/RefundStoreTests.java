package com.messaging.billing.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.messaging.billing.entity.Invoice;
import com.messaging.billing.entity.Payment;
import com.messaging.billing.entity.Refund;
import com.messaging.billing.enums.PaymentStatus;
import com.messaging.billing.enums.RefundStatus;
import com.messaging.billing.repository.PaymentRepository;
import com.messaging.billing.repository.RefundRepository;
import com.messaging.common.exception.ConflictException;
import com.messaging.company.entity.Company;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class RefundStoreTests {

  @Test
  void confirmedSuccessDoesNotRegressOnStaleFailure() {
    RefundRepository refunds = mock(RefundRepository.class);
    PaymentRepository payments = mock(PaymentRepository.class);
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    RefundStore store = new RefundStore(refunds, payments, jdbc);
    Refund refund = refund();

    when(refunds.findByIdForUpdate(40L)).thenReturn(Optional.of(refund));
    when(refunds.saveAndFlush(refund)).thenReturn(refund);

    store.applyRemote(40L, remote("SUCCESS"));
    assertEquals(RefundStatus.SUCCEEDED, refund.getStatus());

    store.applyRemote(40L, remote("REJECTED"));
    assertEquals(RefundStatus.SUCCEEDED, refund.getStatus());
  }

  @Test
  void partialRefundReservationsCannotExceedOriginalSuccessfulPayment() {
    RefundRepository refunds = mock(RefundRepository.class);
    PaymentRepository payments = mock(PaymentRepository.class);
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    RefundStore store = new RefundStore(refunds, payments, jdbc);
    Payment payment = payment();

    when(refunds.findByCompanyIdAndIdempotencyKey(10L, "r2")).thenReturn(Optional.empty());
    when(payments.findByIdForUpdate(20L)).thenReturn(Optional.of(payment));
    when(jdbc.queryForObject(anyString(), eq(BigDecimal.class), eq(20L)))
        .thenReturn(new BigDecimal("900.00"));

    assertThrows(
        ConflictException.class,
        () -> store.prepare(10L, 20L, new BigDecimal("100.01"), "r2"));
    verify(refunds, never()).saveAndFlush(any());
  }

  private Refund refund() {
    Refund refund = new Refund();
    refund.setId(40L);
    refund.setCompanyId(10L);
    refund.setPayment(payment());
    refund.setAmount(new BigDecimal("100.00"));
    refund.setCurrency("INR");
    refund.setIdempotencyKey("r1");
    refund.setProviderRefundId("UDR-40");
    refund.setStatus(RefundStatus.PENDING);
    return refund;
  }

  private Payment payment() {
    Company company = new Company();
    company.setId(10L);
    Invoice invoice = new Invoice();
    invoice.setId(30L);
    invoice.setCompany(company);
    Payment payment = new Payment();
    payment.setId(20L);
    payment.setCompany(company);
    payment.setInvoice(invoice);
    payment.setProviderOrderId("UD-20");
    payment.setAmount(new BigDecimal("1000.00"));
    payment.setCurrency("INR");
    payment.setStatus(PaymentStatus.SUCCEEDED);
    return payment;
  }

  private PaymentProviderGateway.RemoteRefund remote(String status) {
    return new PaymentProviderGateway.RemoteRefund(
        "UDR-40", "cf-refund-1", status, new BigDecimal("100.00"), "INR", null, null);
  }
}
