package com.messaging.billing.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

import com.messaging.billing.entity.Invoice;
import com.messaging.billing.entity.Payment;
import com.messaging.billing.enums.InvoiceStatus;
import com.messaging.billing.enums.PaymentPurpose;
import com.messaging.billing.repository.InvoiceRepository;
import com.messaging.billing.repository.PaymentRepository;
import com.messaging.common.exception.ConflictException;
import com.messaging.company.entity.Company;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PaymentCollectionServiceTests {

  @Test
  void collectsExactCanonicalOpenInvoiceAndReusesOneLogicalPayment() {
    PaymentRepository payments = mock(PaymentRepository.class);
    InvoiceRepository invoices = mock(InvoiceRepository.class);
    PaymentProviderGateway gateway = mock(PaymentProviderGateway.class);
    CashfreeProperties properties = new CashfreeProperties();
    properties.setReturnUrl("https://app.unifydesk.in/return");
    properties.setNotifyUrl("https://api.unifydesk.in/api/v1/webhooks/cashfree");
    PaymentCollectionService service =
        new PaymentCollectionService(payments, invoices, gateway, properties);

    Company company = new Company();
    company.setId(10L);
    Invoice invoice = new Invoice();
    invoice.setId(20L);
    invoice.setCompany(company);
    invoice.setStatus(InvoiceStatus.OPEN);
    invoice.setAmountDue(new BigDecimal("499.00"));
    invoice.setCurrency("INR");
    when(invoices.findByIdForUpdate(20L)).thenReturn(Optional.of(invoice));
    when(payments.findByCompanyIdAndIdempotencyKey(10L, "checkout-1"))
        .thenReturn(Optional.empty());
    when(payments.findByInvoiceId(20L)).thenReturn(Optional.empty());
    when(payments.saveAndFlush(any(Payment.class)))
        .thenAnswer(
            i -> {
              Payment value = i.getArgument(0);
              if (value.getId() == null) value.setId(30L);
              return value;
            });
    when(gateway.createOrder(
            eq("UD-30"),
            eq(new BigDecimal("499.00")),
            eq("INR"),
            any(),
            anyString(),
            anyString(),
            eq("order:30")))
        .thenReturn(new PaymentProviderGateway.Checkout("UD-30", "session-1", "ACTIVE"));

    var result =
        service.createOrResolve(
            10L,
            20L,
            PaymentPurpose.INITIAL_SUBSCRIPTION,
            "checkout-1",
            40L,
            null,
            null,
            new PaymentProviderGateway.Customer(
                "company-10", "Acme", "billing@example.com", "9999999999"));

    assertEquals(new BigDecimal("499.00"), result.amount());
    assertEquals("INR", result.currency());
    verify(gateway, times(1))
        .createOrder(anyString(), any(), anyString(), any(), anyString(), anyString(), anyString());
  }

  @Test
  void rejectsNonInrInvoice() {
    PaymentRepository payments = mock(PaymentRepository.class);
    InvoiceRepository invoices = mock(InvoiceRepository.class);
    PaymentProviderGateway gateway = mock(PaymentProviderGateway.class);
    CashfreeProperties properties = new CashfreeProperties();
    PaymentCollectionService service =
        new PaymentCollectionService(payments, invoices, gateway, properties);

    Company company = new Company();
    company.setId(10L);
    Invoice invoice = new Invoice();
    invoice.setId(20L);
    invoice.setCompany(company);
    invoice.setStatus(InvoiceStatus.OPEN);
    invoice.setAmountDue(BigDecimal.TEN);
    invoice.setCurrency("USD");
    when(payments.findByCompanyIdAndIdempotencyKey(10L, "k")).thenReturn(Optional.empty());
    when(invoices.findByIdForUpdate(20L)).thenReturn(Optional.of(invoice));

    assertThrows(
        ConflictException.class,
        () ->
            service.createOrResolve(
                10L,
                20L,
                PaymentPurpose.RENEWAL,
                "k",
                null,
                null,
                50L,
                new PaymentProviderGateway.Customer("c", "Acme", "x@y.com", "9999999999")));
    verifyNoInteractions(gateway);
  }
}
