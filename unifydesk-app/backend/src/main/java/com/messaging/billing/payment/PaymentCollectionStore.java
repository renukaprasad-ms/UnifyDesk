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
import com.messaging.common.id.IdGenerator;
import java.math.BigDecimal;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Local durable half of payment collection. This bean never performs remote provider I/O. */
@Service
@RequiredArgsConstructor
class PaymentCollectionStore {

  private static final String R1_CURRENCY = "INR";

  private final PaymentRepository paymentRepository;
  private final InvoiceRepository invoiceRepository;

  @Transactional
  Prepared prepare(
      Long companyId,
      Long invoiceId,
      PaymentPurpose purpose,
      String idempotencyKey,
      Long checkoutIntentId,
      Long subscriptionChangeId,
      Long subscriptionId) {
    return prepareInternal(
        companyId,
        invoiceId,
        purpose,
        idempotencyKey,
        checkoutIntentId,
        subscriptionChangeId,
        subscriptionId,
        true);
  }

  @Transactional
  Prepared prepareRecurringRenewal(
      Long companyId,
      Long invoiceId,
      String idempotencyKey,
      Long subscriptionId) {
    return prepareInternal(
        companyId,
        invoiceId,
        PaymentPurpose.RENEWAL,
        idempotencyKey,
        null,
        null,
        subscriptionId,
        false);
  }

  private Prepared prepareInternal(
      Long companyId,
      Long invoiceId,
      PaymentPurpose purpose,
      String idempotencyKey,
      Long checkoutIntentId,
      Long subscriptionChangeId,
      Long subscriptionId,
      boolean createProviderOrder) {
    var prior = paymentRepository.findByCompanyIdAndIdempotencyKey(companyId, idempotencyKey);
    if (prior.isPresent()) {
      assertSame(
          prior.get(),
          invoiceId,
          purpose,
          checkoutIntentId,
          subscriptionChangeId,
          subscriptionId);
      return snapshot(prior.get(), false);
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
      return snapshot(invoicePayment.get(), false);
    }

    Payment payment = new Payment();
    payment.setId(IdGenerator.nextId());
    payment.setCompany(invoice.getCompany());
    payment.setInvoice(invoice);
    payment.setPurpose(purpose);
    payment.setAmount(invoice.getAmountDue());
    payment.setCurrency(invoice.getCurrency());
    payment.setIdempotencyKey(idempotencyKey);
    payment.setProvider("CASHFREE");
    if (createProviderOrder) {
      payment.setProviderOrderId("UD-" + payment.getId());
      payment.setProviderOrderState("CREATE_PENDING");
    } else {
      // Automatic recurring renewal uses Cashfree Subscription CHARGE rather than a one-time PG
      // order. Keeping provider_order_id null prevents the one-time-order reconciler from
      // inventing an unrelated provider order for this logical renewal Payment.
      payment.setProviderOrderId(null);
      payment.setProviderOrderState("NOT_REQUIRED");
      payment.setStatus(PaymentStatus.PENDING);
    }
    payment.setCheckoutIntentId(checkoutIntentId);
    payment.setSubscriptionChangeId(subscriptionChangeId);
    payment.setSubscriptionId(subscriptionId);
    payment = paymentRepository.saveAndFlush(payment);
    return snapshot(payment, true);
  }

  @Transactional
  Prepared acceptProviderOrder(Long paymentId, PaymentProviderGateway.Checkout checkout) {
    Payment payment =
        paymentRepository
            .findByIdForUpdate(paymentId)
            .orElseThrow(() -> new ConflictException("Payment intent no longer exists"));
    if (!payment.getProviderOrderId().equals(checkout.orderId())) {
      throw new ConflictException("Provider returned a conflicting order identity");
    }
    payment.setProviderCheckoutSessionId(checkout.paymentSessionId());
    payment.setProviderOrderStatus(checkout.status());
    payment.setProviderOrderState("CONFIRMED");
    if (payment.getStatus() == PaymentStatus.CREATED) {
      payment.setStatus(PaymentStatus.PENDING);
    }
    return snapshot(paymentRepository.saveAndFlush(payment), false);
  }

  @Transactional
  void markProviderOrderAmbiguous(Long paymentId) {
    paymentRepository
        .findByIdForUpdate(paymentId)
        .ifPresent(
            payment -> {
              if (payment.getStatus() != PaymentStatus.SUCCEEDED) {
                payment.setProviderOrderState("AMBIGUOUS");
                paymentRepository.saveAndFlush(payment);
              }
            });
  }

  private Prepared snapshot(Payment payment, boolean newlyCreated) {
    return new Prepared(
        payment.getId(),
        payment.getInvoice().getId(),
        payment.getProviderOrderId(),
        payment.getProviderCheckoutSessionId(),
        payment.getAmount(),
        payment.getCurrency(),
        payment.getStatus(),
        payment.getProviderOrderState(),
        newlyCreated);
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

  record Prepared(
      Long paymentId,
      Long invoiceId,
      String providerOrderId,
      String paymentSessionId,
      BigDecimal amount,
      String currency,
      PaymentStatus status,
      String providerOrderState,
      boolean newlyCreated) {}
}
