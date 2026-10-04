package com.messaging.billing.payment;

import com.messaging.billing.entity.Payment;
import com.messaging.billing.entity.PaymentAttempt;
import com.messaging.billing.enums.InvoiceStatus;
import com.messaging.billing.enums.PaymentAttemptStatus;
import com.messaging.billing.enums.PaymentPurpose;
import com.messaging.billing.enums.PaymentStatus;
import com.messaging.billing.repository.PaymentAttemptRepository;
import com.messaging.billing.repository.PaymentRepository;
import com.messaging.common.exception.ConflictException;
import com.messaging.common.id.IdGenerator;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
class RecurringChargeStore {

  private final PaymentRepository paymentRepository;
  private final PaymentAttemptRepository attemptRepository;
  private final JdbcTemplate jdbcTemplate;

  @Transactional
  Prepared prepare(Long paymentId, String providerSubscriptionId) {
    Payment payment =
        paymentRepository
            .findByIdForUpdate(paymentId)
            .orElseThrow(() -> new ConflictException("Payment not found for recurring charge"));
    if (payment.getPurpose() != PaymentPurpose.RENEWAL || payment.getSubscriptionId() == null) {
      throw new ConflictException("Recurring collection is only valid for a renewal payment");
    }
    if (payment.getProviderSubscriptionId() == null
        || !payment.getProviderSubscriptionId().equals(providerSubscriptionId)) {
      throw new ConflictException("Payment is not bound to the recurring authorization");
    }
    if (payment.getStatus() == PaymentStatus.SUCCEEDED) {
      return paymentOnly(payment, true);
    }
    if (payment.getInvoice().getStatus() != InvoiceStatus.OPEN
        || payment.getInvoice().getAmountDue().compareTo(payment.getAmount()) != 0
        || !payment.getInvoice().getCurrency().equalsIgnoreCase(payment.getCurrency())) {
      throw new ConflictException(
          "Recurring charge must use the exact unpaid canonical invoice amount");
    }

    List<PaymentAttempt> history =
        attemptRepository.findByPaymentIdOrderByAttemptNoAsc(payment.getId());
    PaymentAttempt latestRecurring = null;
    for (int i = history.size() - 1; i >= 0; i--) {
      if (history.get(i).getProviderCommandId() != null) {
        latestRecurring = history.get(i);
        break;
      }
    }
    if (latestRecurring != null
        && (latestRecurring.getStatus() == PaymentAttemptStatus.CREATED
            || latestRecurring.getStatus() == PaymentAttemptStatus.PENDING)
        && !"FAILED".equals(latestRecurring.getProviderSubmissionState())
        && !"COMMERCIAL_CONFLICT".equals(latestRecurring.getProviderSubmissionState())) {
      return snapshot(payment, latestRecurring, false, false);
    }

    PaymentAttempt attempt = new PaymentAttempt();
    attempt.setId(IdGenerator.nextId());
    attempt.setPayment(payment);
    attempt.setAttemptNo(history.size() + 1);
    attempt.setStatus(PaymentAttemptStatus.CREATED);
    attempt.setAmount(payment.getAmount());
    attempt.setCurrency(payment.getCurrency());
    attempt.setProviderCommandId("UDC-" + attempt.getId());
    attempt.setProviderSubmissionState("CREATE_PENDING");
    attempt = attemptRepository.saveAndFlush(attempt);
    return snapshot(payment, attempt, true, false);
  }

  /**
   * Final fail-closed commercial guard immediately before Cashfree CHARGE. It proves that the
   * immutable Payment/Invoice/PLATFORM period still agrees with the locked subscription boundary
   * and with the accepted scheduled downgrade, if one owns that exact boundary.
   */
  @Transactional(readOnly = true)
  void assertCommerciallyCurrent(Prepared prepared, Instant boundary) {
    if (prepared == null || prepared.attemptId() == null || boundary == null) {
      throw new ConflictException("Recurring commercial boundary identity is required");
    }
    Integer matches =
        jdbcTemplate.queryForObject(
            """
            select count(*)
              from payments p
              join invoices i on i.id = p.invoice_id
              join billing_periods bp on bp.id = i.billing_period_id
              join company_subscriptions s on s.id = p.subscription_id
              left join subscription_changes sc
                on sc.subscription_id = s.id
               and sc.change_type = 'DOWNGRADE'
               and sc.status = 'SCHEDULED'
               and sc.effective_at = bp.start_at
             where p.id = ?
               and p.company_id = ?
               and p.subscription_id = ?
               and p.purpose = 'RENEWAL'
               and p.status <> 'SUCCEEDED'
               and i.status = 'OPEN'
               and bp.kind = 'PLATFORM'
               and bp.start_at = ?
               and bp.company_id = p.company_id
               and bp.plan_id = coalesce(sc.to_plan_id, s.plan_id)
               and bp.plan_price_id = coalesce(sc.to_plan_price_id, s.plan_price_id)
               and bp.platform_amount_snapshot = p.amount
               and i.amount_due = p.amount
               and upper(i.currency) = upper(p.currency)
               and not exists (
                 select 1
                   from subscription_changes stale
                  where stale.subscription_id = s.id
                    and stale.change_type = 'DOWNGRADE'
                    and stale.status = 'SCHEDULED'
                    and stale.effective_at < bp.start_at
               )
            """,
            Integer.class,
            prepared.paymentId(),
            prepared.companyId(),
            prepared.subscriptionId(),
            Timestamp.from(boundary));
    if (matches == null || matches != 1) {
      throw new ConflictException(
          "Recurring provider command no longer matches authoritative commercial state");
    }
  }

  @Transactional
  void markCommercialConflict(Long attemptId) {
    PaymentAttempt attempt =
        attemptRepository
            .findByIdForUpdate(attemptId)
            .orElseThrow(() -> new ConflictException("Recurring payment attempt not found"));
    if (attempt.getStatus() == PaymentAttemptStatus.SUCCEEDED) {
      throw new ConflictException("Successful provider attempt cannot be rewritten as conflict");
    }
    attempt.setStatus(PaymentAttemptStatus.CANCELLED);
    attempt.setProviderSubmissionState("COMMERCIAL_CONFLICT");
    attempt.setFailureCategory("COMMERCIAL_BOUNDARY_CONFLICT");
    attempt.setResolvedAt(Instant.now());
    attemptRepository.saveAndFlush(attempt);
  }

  @Transactional
  Prepared bindRemote(Long attemptId, PaymentProviderGateway.RemoteSubscriptionPayment remote) {
    PaymentAttempt attempt =
        attemptRepository
            .findByIdForUpdate(attemptId)
            .orElseThrow(() -> new ConflictException("Recurring payment attempt not found"));
    Payment payment =
        paymentRepository
            .findByIdForUpdate(attempt.getPayment().getId())
            .orElseThrow(() -> new ConflictException("Payment not found"));
    if (!attempt.getProviderCommandId().equals(remote.paymentId())) {
      throw new ConflictException("Provider returned a conflicting recurring payment identity");
    }
    if (remote.providerPaymentId() != null && !remote.providerPaymentId().isBlank()) {
      if (attempt.getProviderPaymentId() != null
          && !attempt.getProviderPaymentId().equals(remote.providerPaymentId())) {
        throw new ConflictException("Recurring command has a conflicting provider payment identity");
      }
      attemptRepository
          .findByProviderPaymentId(remote.providerPaymentId())
          .filter(other -> !other.getId().equals(attempt.getId()))
          .ifPresent(
              other -> {
                throw new ConflictException(
                    "Provider payment identity is already bound to another recurring attempt");
              });
      if (attempt.getProviderPaymentId() == null) {
        attempt.setProviderPaymentId(remote.providerPaymentId());
      }
    }
    if (attempt.getProviderCreatedAt() == null) {
      attempt.setProviderCreatedAt(remote.providerCreatedAt());
    }
    attempt.setProviderSubmissionState("CONFIRMED");
    attemptRepository.saveAndFlush(attempt);
    return snapshot(payment, attempt, false, false);
  }

  @Transactional
  void markAmbiguous(Long attemptId) {
    attemptRepository
        .findById(attemptId)
        .ifPresent(
            attempt -> {
              if (attempt.getStatus() != PaymentAttemptStatus.SUCCEEDED
                  && !"COMMERCIAL_CONFLICT".equals(attempt.getProviderSubmissionState())) {
                attempt.setProviderSubmissionState("AMBIGUOUS");
                if (attempt.getStatus() == PaymentAttemptStatus.CREATED) {
                  attempt.setStatus(PaymentAttemptStatus.PENDING);
                }
                attemptRepository.saveAndFlush(attempt);
              }
            });
  }

  @Transactional(readOnly = true)
  Prepared require(Long attemptId) {
    PaymentAttempt attempt =
        attemptRepository
            .findById(attemptId)
            .orElseThrow(() -> new ConflictException("Recurring payment attempt not found"));
    return snapshot(attempt.getPayment(), attempt, false, false);
  }

  private Prepared paymentOnly(Payment payment, boolean logicalSucceeded) {
    return new Prepared(
        payment.getId(),
        null,
        payment.getCompany().getId(),
        payment.getSubscriptionId(),
        payment.getProviderSubscriptionId(),
        payment.getProviderOrderId(),
        null,
        null,
        payment.getAmount(),
        payment.getCurrency(),
        false,
        logicalSucceeded);
  }

  private Prepared snapshot(
      Payment payment, PaymentAttempt attempt, boolean newlyCreated, boolean logicalSucceeded) {
    return new Prepared(
        payment.getId(),
        attempt.getId(),
        payment.getCompany().getId(),
        payment.getSubscriptionId(),
        payment.getProviderSubscriptionId(),
        payment.getProviderOrderId(),
        attempt.getProviderCommandId(),
        attempt.getProviderPaymentId(),
        payment.getAmount(),
        payment.getCurrency(),
        newlyCreated,
        logicalSucceeded);
  }

  record Prepared(
      Long paymentId,
      Long attemptId,
      Long companyId,
      Long subscriptionId,
      String providerSubscriptionId,
      String providerOrderId,
      String providerCommandId,
      String providerPaymentId,
      BigDecimal amount,
      String currency,
      boolean newlyCreated,
      boolean logicalSucceeded) {}
}
