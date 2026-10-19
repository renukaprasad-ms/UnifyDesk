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
  private final RecurringAuthorizationService recurringAuthorizationService;
  private final JdbcTemplate jdbcTemplate;
  private final com.messaging.realtime.service.RealtimeService realtimeService;

  @Transactional
  public void confirmSuccess(
      String providerOrderId,
      String providerPaymentId,
      BigDecimal amount,
      String currency,
      Instant providerCreatedAt) {
    Payment payment =
        paymentRepository
            .findByProviderAndProviderOrderId("RAZORPAY", providerOrderId)
            .or(() -> paymentRepository.findByProviderAndProviderOrderId("CASHFREE", providerOrderId))
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
    applyConfirmedSuccess(payment, providerCreatedAt);
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
            .findByProviderAndProviderOrderId("RAZORPAY", providerOrderId)
            .or(() -> paymentRepository.findByProviderAndProviderOrderId("CASHFREE", providerOrderId))
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
    applyNonSuccess(payment, attemptStatus, providerCreatedAt);
  }

  /**
   * Command-first recurring confirmation boundary.
   *
   * <p>The durable recurring PaymentAttempt already exists before provider I/O. Both synchronous
   * HTTP responses and recurring webhooks must converge on that attempt by provider_command_id
   * before providerPaymentId is considered.
   */
  @Transactional
  public void confirmRecurringAttempt(
      String commandId,
      String providerSubscriptionId,
      String providerPaymentId,
      BigDecimal amount,
      String currency,
      PaymentAttemptStatus status,
      String failureCategory,
      Instant providerCreatedAt) {
    if (commandId == null || commandId.isBlank()) {
      throw new ConflictException("Recurring provider command identity is required");
    }
    PaymentAttempt attempt =
        attemptRepository
            .findByProviderCommandIdForUpdate(commandId)
            .orElseThrow(() -> new ConflictException("Recurring provider command is not bound locally"));
    Payment payment =
        paymentRepository
            .findByIdForUpdate(attempt.getPayment().getId())
            .orElseThrow(() -> new ConflictException("Recurring payment disappeared"));

    if (payment.getPurpose() != PaymentPurpose.RENEWAL
        || payment.getSubscriptionId() == null
        || payment.getProviderSubscriptionId() == null
        || providerSubscriptionId == null
        || !payment.getProviderSubscriptionId().equals(providerSubscriptionId)) {
      throw new ConflictException("Recurring provider command does not match payment ownership");
    }
    validateFinancialIdentity(payment, amount, currency);
    if (attempt.getAmount().compareTo(payment.getAmount()) != 0
        || !attempt.getCurrency().equalsIgnoreCase(payment.getCurrency())) {
      throw new ConflictException("Recurring attempt financial identity conflicts with payment");
    }

    bindProviderPaymentIdentity(attempt, providerPaymentId, status);
    transitionAttempt(attempt, status, failureCategory, providerCreatedAt);
    attempt.setProviderSubmissionState("CONFIRMED");
    attemptRepository.saveAndFlush(attempt);

    if (status == PaymentAttemptStatus.SUCCEEDED) {
      applyConfirmedSuccess(payment, providerCreatedAt);
    } else {
      applyNonSuccess(payment, status, providerCreatedAt);
    }
  }

  /**
   * Due boundary fallback when no ACTIVE recurring authorization exists. This records no provider
   * attempt because no provider collection command was launched.
   */
  @Transactional
  public void markRenewalDueUncollectible(
      Long paymentId, Instant dueBoundary, String failureCategory) {
    Payment payment =
        paymentRepository
            .findByIdForUpdate(paymentId)
            .orElseThrow(() -> new ConflictException("Renewal payment not found"));
    if (payment.getPurpose() != PaymentPurpose.RENEWAL || payment.getSubscriptionId() == null) {
      throw new ConflictException("Payment is not a recurring renewal");
    }
    if (payment.getStatus() == PaymentStatus.SUCCEEDED || !renewalDue(payment, dueBoundary)) {
      return;
    }

    payment.setStatus(PaymentStatus.FAILED);
    paymentRepository.saveAndFlush(payment);
    markPastDueAndSchedule(
        payment,
        dueBoundary == null ? Instant.now() : dueBoundary,
        failureCategory == null ? "RECURRING_AUTHORIZATION_NOT_ACTIVE" : failureCategory);
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

  private void applyConfirmedSuccess(Payment payment, Instant providerCreatedAt) {
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
    try {
      realtimeService.sendToCompany(
          payment.getCompany().getId(),
          "billing.payment.updated",
          java.util.Map.of(
              "companyId", payment.getCompany().getId(),
              "paymentId", payment.getId(),
              "orderId", payment.getProviderOrderId() != null ? payment.getProviderOrderId() : "",
              "status", "SUCCEEDED",
              "amount", payment.getAmount()));
    } catch (Exception ignored) {
    }
  }

  private void applyNonSuccess(
      Payment payment, PaymentAttemptStatus attemptStatus, Instant providerCreatedAt) {
    // Success is terminal for the logical payment: stale/out-of-order failures cannot regress it.
    if (payment.getStatus() == PaymentStatus.SUCCEEDED) {
      return;
    }

    payment.setStatus(
        attemptStatus == PaymentAttemptStatus.PENDING
                || attemptStatus == PaymentAttemptStatus.CREATED
            ? PaymentStatus.PENDING
            : PaymentStatus.FAILED);
    paymentRepository.saveAndFlush(payment);

    if (payment.getPurpose() == PaymentPurpose.RENEWAL
        && payment.getSubscriptionId() != null
        && attemptStatus != PaymentAttemptStatus.PENDING
        && attemptStatus != PaymentAttemptStatus.CREATED) {
      Instant failedAt = providerCreatedAt == null ? Instant.now() : providerCreatedAt;
      if (renewalDue(payment, failedAt)) {
        markPastDueAndSchedule(payment, failedAt, "RENEWAL_COLLECTION_FAILED");
      }
    }
  }

  private void markPastDueAndSchedule(
      Payment payment, Instant occurredAt, String failureCategory) {
    Instant boundary = renewalBoundary(payment);
    Instant effectiveAt = boundary == null ? occurredAt : boundary;
    var subscription = lifecycleService.require(payment.getSubscriptionId());
    if (subscription.getStatus() == SubscriptionStatus.ACTIVE) {
      // The R1 grace clock is anchored to the canonical renewal boundary, not to a late
      // scheduler/provider callback timestamp.
      lifecycleService.markPastDue(payment.getSubscriptionId(), effectiveAt);
    }
    scheduleDunning(
        payment.getCompany().getId(),
        payment.getSubscriptionId(),
        payment.getInvoice().getId(),
        payment.getId(),
        effectiveAt,
        failureCategory);
  }

  private boolean renewalDue(Payment payment, Instant at) {
    Instant boundary = renewalBoundary(payment);
    return boundary != null && at != null && !at.isBefore(boundary);
  }

  private Instant renewalBoundary(Payment payment) {
    return payment.getInvoice().getServicePeriodStart();
  }

  private void applyLifecycle(Payment payment, Instant confirmedAt) {
    switch (payment.getPurpose()) {
      case INITIAL_SUBSCRIPTION -> {
        if (payment.getCheckoutIntentId() == null) {
          throw new ConflictException("Initial payment has no canonical checkout intent");
        }
        var activated =
            checkoutService.activateConfirmedInitialCheckout(
                payment.getCompany().getId(),
                payment.getCheckoutIntentId(),
                payment.getInvoice().getId(),
                confirmedAt);
        recurringAuthorizationService.bindInitialActivation(
            payment.getCompany().getId(),
            payment.getCheckoutIntentId(),
            Long.valueOf(activated.subscriptionId()));
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

  private void bindProviderPaymentIdentity(
      PaymentAttempt attempt, String providerPaymentId, PaymentAttemptStatus status) {
    String normalized =
        providerPaymentId == null || providerPaymentId.isBlank()
            ? null
            : providerPaymentId.trim();
    if (status == PaymentAttemptStatus.SUCCEEDED && normalized == null) {
      throw new ConflictException("Successful recurring payment has no provider payment identity");
    }
    if (normalized == null) {
      return;
    }
    if (attempt.getProviderPaymentId() != null
        && !attempt.getProviderPaymentId().equals(normalized)) {
      throw new ConflictException("Recurring command returned a conflicting provider payment identity");
    }
    var other = attemptRepository.findByProviderPaymentId(normalized);
    if (other.isPresent() && !other.get().getId().equals(attempt.getId())) {
      throw new ConflictException("Provider payment identity is already bound to another attempt");
    }
    if (attempt.getProviderPaymentId() == null) {
      attempt.setProviderPaymentId(normalized);
    }
  }

  private void transitionAttempt(
      PaymentAttempt attempt,
      PaymentAttemptStatus status,
      String failureCategory,
      Instant providerCreatedAt) {
    if (attempt.getStatus() == PaymentAttemptStatus.SUCCEEDED) {
      return;
    }
    if (status == PaymentAttemptStatus.SUCCEEDED) {
      attempt.setStatus(PaymentAttemptStatus.SUCCEEDED);
      attempt.setFailureCategory(null);
      attempt.setResolvedAt(Instant.now());
    } else if (attempt.getStatus() == PaymentAttemptStatus.CREATED
        || (attempt.getStatus() == PaymentAttemptStatus.PENDING
            && status != PaymentAttemptStatus.PENDING)) {
      attempt.setStatus(status);
      attempt.setFailureCategory(failureCategory);
      if (status != PaymentAttemptStatus.PENDING && status != PaymentAttemptStatus.CREATED) {
        attempt.setResolvedAt(Instant.now());
      }
    }
    if (attempt.getProviderCreatedAt() == null) {
      attempt.setProviderCreatedAt(providerCreatedAt);
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
        transitionAttempt(prior, status, failureCategory, providerCreatedAt);
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
      Instant dueBoundary,
      String failureCategory) {
    Instant base = dueBoundary == null ? Instant.now() : dueBoundary;
    jdbcTemplate.update(
        """
        insert into payment_dunning_jobs (
          id, company_id, subscription_id, invoice_id, payment_id, status,
          attempt_count, dunning_stage, next_attempt_at, grace_ends_at, created_at, updated_at
        ) values (?, ?, ?, ?, ?, 'ACTIVE', 1, 'DUE_FAILED', ?, ?, now(), now())
        on conflict (invoice_id) do update
          set status = case
                         when payment_dunning_jobs.status = 'RECOVERED' then 'RECOVERED'
                         else payment_dunning_jobs.status
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
