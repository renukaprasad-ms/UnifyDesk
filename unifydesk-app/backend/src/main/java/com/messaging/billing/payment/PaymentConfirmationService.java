package com.messaging.billing.payment;

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
import com.messaging.common.id.IdGenerator;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentConfirmationService {

  private final PaymentRepository paymentRepository;
  private final PaymentAttemptRepository attemptRepository;
  private final InvoiceService invoiceService;
  private final SubscriptionCheckoutService checkoutService;
  private final SubscriptionChangeService changeService;
  private final SubscriptionLifecycleService lifecycleService;
  private final JdbcTemplate jdbcTemplate;

  @Transactional
  public void confirmSuccess(
      String providerOrderId,
      String providerPaymentId,
      BigDecimal amount,
      String currency,
      Instant providerCreatedAt) {
    Payment payment =
        paymentRepository
            .findByProviderAndProviderOrderId("CASHFREE", providerOrderId)
            .orElseThrow(() -> new ConflictException("Provider order is not bound to a payment"));
    confirmSuccessByPaymentId(
        payment.getId(), providerPaymentId, amount, currency, providerCreatedAt);
  }

  @Transactional
  public void confirmSuccessByPaymentId(
      Long paymentId,
      String providerPaymentId,
      BigDecimal amount,
      String currency,
      Instant providerCreatedAt) {
    Payment payment =
        paymentRepository
            .findByIdForUpdate(paymentId)
            .orElseThrow(() -> new ConflictException("Payment disappeared during confirmation"));

    validateFinancialIdentity(payment, amount, currency);
    ensureAttempt(
        payment,
        providerPaymentId,
        PaymentAttemptStatus.SUCCEEDED,
        null,
        providerCreatedAt);

    Instant confirmedAt = providerCreatedAt == null ? Instant.now() : providerCreatedAt;
    if (payment.getStatus() != PaymentStatus.SUCCEEDED) {
      payment.setStatus(PaymentStatus.SUCCEEDED);
      payment.setSucceededAt(confirmedAt);
      paymentRepository.saveAndFlush(payment);
      invoiceService.markPaidConfirmed(payment.getInvoice().getId(), confirmedAt);
    }

    if (payment.getLifecycleAppliedAt() == null) {
      if (payment.getPurpose() != PaymentPurpose.RENEWAL || renewalDue(payment, confirmedAt)) {
        applyLifecycle(payment, confirmedAt);
        payment.setLifecycleAppliedAt(confirmedAt);
        paymentRepository.saveAndFlush(payment);
      }
    }
    completeDunning(payment.getId());
  }

  @Transactional
  public void recordFailure(
      String providerOrderId,
      String providerPaymentId,
      BigDecimal amount,
      String currency,
      String failureCategory,
      PaymentAttemptStatus attemptStatus,
      Instant providerCreatedAt) {
    Payment payment =
        paymentRepository
            .findByProviderAndProviderOrderId("CASHFREE", providerOrderId)
            .orElseThrow(() -> new ConflictException("Provider order is not bound to a payment"));
    recordFailureByPaymentId(
        payment.getId(),
        providerPaymentId,
        amount,
        currency,
        failureCategory,
        attemptStatus,
        providerCreatedAt);
  }

  @Transactional
  public void recordFailureByPaymentId(
      Long paymentId,
      String providerPaymentId,
      BigDecimal amount,
      String currency,
      String failureCategory,
      PaymentAttemptStatus attemptStatus,
      Instant providerCreatedAt) {
    Payment payment =
        paymentRepository
            .findByIdForUpdate(paymentId)
            .orElseThrow(() -> new ConflictException("Payment disappeared during failure update"));
    validateFinancialIdentity(payment, amount, currency);
    ensureAttempt(
        payment,
        providerPaymentId,
        attemptStatus,
        failureCategory,
        providerCreatedAt);

    // Success is terminal for the logical payment: stale/out-of-order failures cannot regress it.
    if (payment.getStatus() == PaymentStatus.SUCCEEDED) return;

    payment.setStatus(
        attemptStatus == PaymentAttemptStatus.PENDING
            ? PaymentStatus.PENDING
            : PaymentStatus.FAILED);
    paymentRepository.saveAndFlush(payment);

    if (payment.getPurpose() == PaymentPurpose.RENEWAL
        && payment.getSubscriptionId() != null
        && attemptStatus != PaymentAttemptStatus.PENDING) {
      Instant failedAt = providerCreatedAt == null ? Instant.now() : providerCreatedAt;
      if (renewalDue(payment, failedAt)) {
        var subscription = lifecycleService.require(payment.getSubscriptionId());
        if (subscription.getStatus() == SubscriptionStatus.ACTIVE) {
          lifecycleService.markPastDue(payment.getSubscriptionId(), failedAt);
        }
        scheduleDunning(
            payment.getCompany().getId(),
            payment.getSubscriptionId(),
            payment.getInvoice().getId(),
            payment.getId(),
            renewalBoundary(payment));
      }
    }
  }

  @Transactional
  public void applyDeferredRenewal(Long paymentId, Instant now) {
    Payment payment =
        paymentRepository
            .findByIdForUpdate(paymentId)
            .orElseThrow(() -> new ConflictException("Renewal payment not found"));
    if (payment.getPurpose() != PaymentPurpose.RENEWAL
        || payment.getStatus() != PaymentStatus.SUCCEEDED
        || payment.getLifecycleAppliedAt() != null
        || !renewalDue(payment, now)) {
      return;
    }
    applyLifecycle(payment, now);
    payment.setLifecycleAppliedAt(now);
    paymentRepository.saveAndFlush(payment);
    completeDunning(payment.getId());
  }

  private boolean renewalDue(Payment payment, Instant at) {
    Instant boundary = renewalBoundary(payment);
    return boundary != null && at != null && !at.isBefore(boundary);
  }

  private Instant renewalBoundary(Payment payment) {
    // The next invoice service-period start is the current subscription period end.
    // It is the canonical B1 renewal boundary even when an invoice is prepared early.
    return payment.getInvoice().getServicePeriodStart();
  }

  private void applyLifecycle(Payment payment, Instant confirmedAt) {
    switch (payment.getPurpose()) {
      case INITIAL_SUBSCRIPTION -> {
        if (payment.getCheckoutIntentId() == null) {
          throw new ConflictException("Initial payment has no canonical checkout intent");
        }
        checkoutService.activateConfirmedInitialCheckout(
            payment.getCompany().getId(),
            payment.getCheckoutIntentId(),
            payment.getInvoice().getId(),
            confirmedAt);
      }
      case UPGRADE -> {
        if (payment.getSubscriptionChangeId() == null) {
          throw new ConflictException("Upgrade payment has no subscription change");
        }
        changeService.applyConfirmedUpgrade(
            payment.getSubscriptionChangeId(), payment.getInvoice().getId(), confirmedAt);
      }
      case RENEWAL -> {
        if (payment.getSubscriptionId() == null) {
          throw new ConflictException("Renewal payment has no subscription");
        }
        lifecycleService.renewAfterConfirmedCollection(
            payment.getSubscriptionId(), payment.getInvoice().getId(), confirmedAt);
      }
      case MANAGED_USAGE -> {
        // Invoice paid is the complete lifecycle effect for monthly Managed-Service usage.
      }
    }
  }

  private void validateFinancialIdentity(Payment payment, BigDecimal amount, String currency) {
    if (amount == null || payment.getAmount().compareTo(amount) != 0) {
      throw new ConflictException("Provider payment amount does not match canonical invoice amount");
    }
    if (currency == null
        || !"INR".equalsIgnoreCase(currency)
        || !payment.getCurrency().equalsIgnoreCase(currency)) {
      throw new ConflictException("Provider payment currency does not match canonical invoice");
    }
    if (!payment.getInvoice().getCompany().getId().equals(payment.getCompany().getId())) {
      throw new ConflictException("Payment and invoice company ownership mismatch");
    }
  }

  private void ensureAttempt(
      Payment payment,
      String providerPaymentId,
      PaymentAttemptStatus status,
      String failureCategory,
      Instant providerCreatedAt) {
    if (providerPaymentId != null && !providerPaymentId.isBlank()) {
      var existing = attemptRepository.findByProviderPaymentId(providerPaymentId);
      if (existing.isPresent()) {
        PaymentAttempt prior = existing.get();
        if (!prior.getPayment().getId().equals(payment.getId())
            || prior.getAmount().compareTo(payment.getAmount()) != 0
            || !prior.getCurrency().equalsIgnoreCase(payment.getCurrency())) {
          throw new ConflictException("Provider payment identity conflicts with local payment");
        }
        if (prior.getStatus() == PaymentAttemptStatus.SUCCEEDED) return;
        if (status == PaymentAttemptStatus.SUCCEEDED) {
          prior.setStatus(status);
          prior.setFailureCategory(null);
          prior.setResolvedAt(Instant.now());
        } else if (prior.getStatus() == PaymentAttemptStatus.CREATED
            || (prior.getStatus() == PaymentAttemptStatus.PENDING
                && status != PaymentAttemptStatus.PENDING)) {
          prior.setStatus(status);
          prior.setFailureCategory(failureCategory);
          if (status != PaymentAttemptStatus.PENDING) {
            prior.setResolvedAt(Instant.now());
          }
        }
        if (prior.getProviderCreatedAt() == null) {
          prior.setProviderCreatedAt(providerCreatedAt);
        }
        attemptRepository.saveAndFlush(prior);
        return;
      }
    }

    List<PaymentAttempt> history =
        attemptRepository.findByPaymentIdOrderByAttemptNoAsc(payment.getId());
    PaymentAttempt attempt = new PaymentAttempt();
    attempt.setPayment(payment);
    attempt.setAttemptNo(history.size() + 1);
    attempt.setStatus(status);
    attempt.setAmount(payment.getAmount());
    attempt.setCurrency(payment.getCurrency());
    attempt.setProviderPaymentId(
        providerPaymentId == null || providerPaymentId.isBlank()
            ? null
            : providerPaymentId.trim());
    attempt.setFailureCategory(failureCategory);
    attempt.setProviderCreatedAt(providerCreatedAt);
    if (status != PaymentAttemptStatus.PENDING && status != PaymentAttemptStatus.CREATED) {
      attempt.setResolvedAt(Instant.now());
    }
    attemptRepository.saveAndFlush(attempt);
  }

  private void scheduleDunning(
      Long companyId,
      Long subscriptionId,
      Long invoiceId,
      Long paymentId,
      Instant dueBoundary) {
    Instant base = dueBoundary == null ? Instant.now() : dueBoundary;
    jdbcTemplate.update(
        """
        insert into payment_dunning_jobs (
          id, company_id, subscription_id, invoice_id, payment_id, status,
          attempt_count, next_attempt_at, grace_ends_at, created_at, updated_at
        ) values (?, ?, ?, ?, ?, 'ACTIVE', 1, ?, ?, now(), now())
        on conflict (invoice_id) do update
          set status = case
                         when payment_dunning_jobs.status = 'RECOVERED' then 'RECOVERED'
                         else 'ACTIVE'
                       end,
              next_attempt_at = case
                                  when payment_dunning_jobs.status = 'RECOVERED'
                                    then payment_dunning_jobs.next_attempt_at
                                  else least(payment_dunning_jobs.next_attempt_at, excluded.next_attempt_at)
                                end,
              updated_at = now()
        """,
        IdGenerator.nextId(),
        companyId,
        subscriptionId,
        invoiceId,
        paymentId,
        Timestamp.from(base.plus(Duration.ofDays(2))),
        Timestamp.from(base.plus(Duration.ofDays(7))));
  }

  private void completeDunning(Long paymentId) {
    jdbcTemplate.update(
        """
        update payment_dunning_jobs
           set status='RECOVERED', next_attempt_at=null,
               claim_token=null, claimed_until=null, updated_at=now()
         where payment_id=? and status in ('ACTIVE','CLAIMED')
        """,
        paymentId);
  }
}
