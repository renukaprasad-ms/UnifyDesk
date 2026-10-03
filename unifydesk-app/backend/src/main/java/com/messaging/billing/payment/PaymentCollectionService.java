package com.messaging.billing.payment;

import com.messaging.billing.entity.Invoice;
import com.messaging.billing.entity.Payment;
import com.messaging.billing.enums.InvoiceStatus;
import com.messaging.billing.enums.PaymentPurpose;
import com.messaging.billing.enums.PaymentStatus;
import com.messaging.billing.repository.InvoiceRepository;
import com.messaging.billing.repository.PaymentRepository;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.ConflictException;
import java.math.BigDecimal;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentCollectionService {

  private static final String R1_CURRENCY = "INR";

  private final PaymentRepository paymentRepository;
  private final InvoiceRepository invoiceRepository;
  private final PaymentProviderGateway gateway;
  private final CashfreeProperties cashfreeProperties;

  @Transactional
  public CheckoutResult createOrResolve(
      Long companyId,
      Long invoiceId,
      PaymentPurpose purpose,
      String idempotencyKey,
      Long checkoutIntentId,
      Long subscriptionChangeId,
      Long subscriptionId,
      PaymentProviderGateway.Customer customer) {
    String key = required(idempotencyKey);
    var prior = paymentRepository.findByCompanyIdAndIdempotencyKey(companyId, key);
    if (prior.isPresent()) {
      assertSame(
          prior.get(),
          invoiceId,
          purpose,
          checkoutIntentId,
          subscriptionChangeId,
          subscriptionId);
      return toResult(prior.get(), null);
    }

    Invoice invoice =
        invoiceRepository
            .findByIdForUpdate(invoiceId)
            .orElseThrow(() -> new BadRequestException("Invoice not found"));
    if (!invoice.getCompany().getId().equals(companyId)) {
      throw new BadRequestException("Invoice does not belong to the company");
    }
    if (invoice.getStatus() != InvoiceStatus.OPEN || invoice.getAmountDue().signum() <= 0) {
      throw new ConflictException("Only a positive open canonical invoice can be collected");
    }
    if (!R1_CURRENCY.equalsIgnoreCase(invoice.getCurrency())) {
      throw new ConflictException("Billing R1 collections require INR");
    }

    var invoicePayment = paymentRepository.findByInvoiceId(invoiceId);
    if (invoicePayment.isPresent()) {
      assertSame(
          invoicePayment.get(),
          invoiceId,
          purpose,
          checkoutIntentId,
          subscriptionChangeId,
          subscriptionId);
      return toResult(invoicePayment.get(), null);
    }

    Payment payment = new Payment();
    payment.setCompany(invoice.getCompany());
    payment.setInvoice(invoice);
    payment.setPurpose(purpose);
    payment.setAmount(invoice.getAmountDue());
    payment.setCurrency(invoice.getCurrency());
    payment.setIdempotencyKey(key);
    payment.setProvider("CASHFREE");
    payment.setCheckoutIntentId(checkoutIntentId);
    payment.setSubscriptionChangeId(subscriptionChangeId);
    payment.setSubscriptionId(subscriptionId);
    payment = paymentRepository.saveAndFlush(payment);

    String providerOrderId = "UD-" + payment.getId();
    payment.setProviderOrderId(providerOrderId);
    payment = paymentRepository.saveAndFlush(payment);

    PaymentProviderGateway.Checkout checkout =
        gateway.createOrder(
            providerOrderId,
            payment.getAmount(),
            payment.getCurrency(),
            customer,
            cashfreeProperties.getReturnUrl(),
            cashfreeProperties.getNotifyUrl(),
            "order:" + payment.getId());
    if (!providerOrderId.equals(checkout.orderId())) {
      throw new ConflictException("Cashfree returned a conflicting order identity");
    }
    payment.setStatus(PaymentStatus.PENDING);
    payment = paymentRepository.saveAndFlush(payment);
    return toResult(payment, checkout.paymentSessionId());
  }

  private CheckoutResult toResult(Payment payment, String sessionId) {
    return new CheckoutResult(
        payment.getId(),
        payment.getInvoice().getId(),
        payment.getProviderOrderId(),
        sessionId,
        payment.getAmount(),
        payment.getCurrency(),
        payment.getStatus());
  }

  private void assertSame(
      Payment payment,
      Long invoiceId,
      PaymentPurpose purpose,
      Long checkoutIntentId,
      Long subscriptionChangeId,
      Long subscriptionId) {
    if (!payment.getInvoice().getId().equals(invoiceId)
        || payment.getPurpose() != purpose
        || !Objects.equals(payment.getCheckoutIntentId(), checkoutIntentId)
        || !Objects.equals(payment.getSubscriptionChangeId(), subscriptionChangeId)
        || !Objects.equals(payment.getSubscriptionId(), subscriptionId)) {
      throw new ConflictException(
          "Payment idempotency key was reused for a materially different financial command");
    }
  }

  private String required(String value) {
    if (value == null || value.isBlank() || value.length() > 255) {
      throw new BadRequestException("A valid payment idempotency key is required");
    }
    return value.trim();
  }

  public record CheckoutResult(
      Long paymentId,
      Long invoiceId,
      String providerOrderId,
      String paymentSessionId,
      BigDecimal amount,
      String currency,
      PaymentStatus status) {}
}
