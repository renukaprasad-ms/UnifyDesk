package com.messaging.billing.payment;

import com.messaging.billing.enums.PaymentStatus;
import com.messaging.billing.service.SubscriptionLifecycleService;
import com.messaging.common.id.IdGenerator;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DunningService {

  private static final Duration GRACE = Duration.ofDays(7);
  private static final Duration OBSERVATION_DELAY = Duration.ofHours(1);

  static final String DUE_FAILED = "DUE_FAILED";
  static final String D2_FAILED = "D2_FAILED";
  static final String D5_FAILED = "D5_FAILED";
  static final String GRACE_EXHAUSTED = "GRACE_EXHAUSTED";

  private final JdbcTemplate jdbcTemplate;
  private final PaymentReconciliationService reconciliationService;
  private final RecurringChargeService recurringChargeService;
  private final SubscriptionLifecycleService lifecycleService;
  private final CashfreeProperties properties;

  public void schedule(
      Long companyId, Long subscriptionId, Long invoiceId, Long paymentId, Instant dueBoundary) {
    Instant base = dueBoundary == null ? Instant.now() : dueBoundary;
    jdbcTemplate.update(
        """
        insert into payment_dunning_jobs (
          id, company_id, subscription_id, invoice_id, payment_id, status,
          attempt_count, dunning_stage, next_attempt_at, grace_ends_at, created_at, updated_at
        ) values (?, ?, ?, ?, ?, 'ACTIVE', 1, 'DUE_FAILED', ?, ?, now(), now())
        on conflict (invoice_id) do nothing
        """,
        IdGenerator.nextId(),
        companyId,
        subscriptionId,
        invoiceId,
        paymentId,
        Timestamp.from(base.plus(Duration.ofDays(2))),
        Timestamp.from(base.plus(GRACE)));
  }

  @Scheduled(
      fixedDelayString = "${app.billing.cashfree.dunning-interval-ms:300000}",
      initialDelayString = "${app.billing.cashfree.dunning-initial-delay-ms:60000}")
  public void run() {
    for (Job job : claimDue()) {
      processClaimed(job);
    }
  }

  List<Job> claimDue() {
    String token = UUID.randomUUID().toString();
    long claimSeconds = Math.max(30L, properties.getDunningClaimSeconds());
    return jdbcTemplate.query(
        """
        with due as (
          select id
            from payment_dunning_jobs
           where next_attempt_at <= now()
             and (
               status = 'ACTIVE'
               or (status = 'CLAIMED' and claimed_until < now())
             )
           order by next_attempt_at, id
           for update skip locked
           limit 50
        )
        update payment_dunning_jobs j
           set status = 'CLAIMED',
               claim_token = ?,
               claimed_until = now() + (? * interval '1 second'),
               updated_at = now()
          from due
         where j.id = due.id
        returning j.id, j.subscription_id, j.payment_id, j.attempt_count,
                  j.dunning_stage, j.grace_ends_at, j.claim_token
        """,
        (rs, rowNum) ->
            new Job(
                rs.getLong("id"),
                rs.getLong("subscription_id"),
                rs.getLong("payment_id"),
                rs.getInt("attempt_count"),
                rs.getString("dunning_stage"),
                rs.getTimestamp("grace_ends_at").toInstant(),
                rs.getString("claim_token")),
        token,
        claimSeconds);
  }

  void processClaimed(Job job) {
    Instant now = Instant.now();
    if (!now.isBefore(job.graceEndsAt())) {
      lifecycleService.applyDueLifecycle(now);
      finish(job, "EXHAUSTED", null, job.attemptCount(), GRACE_EXHAUSTED);
      return;
    }

    try {
      reconcileOneTimeOrderIfPresent(job.paymentId());
      if (paymentStatus(job.paymentId()) == PaymentStatus.SUCCEEDED) {
        finish(job, "RECOVERED", null, job.attemptCount(), job.stage());
        return;
      }

      Long unresolvedBefore = latestUnresolvedRecurringAttempt(job.paymentId());
      if (unresolvedBefore != null) {
        try {
          recurringChargeService.reconcileAttempt(unresolvedBefore);
        } catch (RuntimeException ignored) {
          releaseObservation(job, now);
          return;
        }

        if (paymentStatus(job.paymentId()) == PaymentStatus.SUCCEEDED) {
          finish(job, "RECOVERED", null, job.attemptCount(), job.stage());
          return;
        }
        if (latestUnresolvedRecurringAttempt(job.paymentId()) != null) {
          releaseObservation(job, now);
          return;
        }

        // The command that was already launched for this stage is now definitively failed.
        // Consume this stage exactly once; do not immediately launch the next retry.
        advanceAfterDefinitiveFailure(job);
        return;
      }

      if (D5_FAILED.equals(job.stage())) {
        // D+5 already failed. The only remaining transition is D+7 grace exhaustion.
        release(job, job.graceEndsAt(), job.attemptCount(), D5_FAILED);
        return;
      }

      try {
        recurringChargeService.charge(job.paymentId(), now);
      } catch (RuntimeException ignored) {
        // RecurringChargeService persists the command before provider I/O. If the provider
        // outcome is ambiguous, keep this stage and reconcile that same command identity.
        releaseObservation(job, now);
        return;
      }

      if (paymentStatus(job.paymentId()) == PaymentStatus.SUCCEEDED) {
        finish(job, "RECOVERED", null, job.attemptCount(), job.stage());
        return;
      }
      if (latestUnresolvedRecurringAttempt(job.paymentId()) != null) {
        releaseObservation(job, now);
        return;
      }

      // A synchronous terminal failure consumed the retry represented by the current stage.
      advanceAfterDefinitiveFailure(job);
    } catch (RuntimeException ignored) {
      // Provider/local truth is inconclusive. Observe again without consuming D+2 or D+5.
      releaseObservation(job, now);
    }
  }

  private void reconcileOneTimeOrderIfPresent(Long paymentId) {
    List<String> ids =
        jdbcTemplate.queryForList(
            """
            select provider_order_id
              from payments
             where id=?
               and provider_order_id is not null
               and provider_order_state <> 'NOT_REQUIRED'
            """,
            String.class,
            paymentId);
    if (!ids.isEmpty()) {
      reconciliationService.reconcile(paymentId);
    }
  }

  private Long latestUnresolvedRecurringAttempt(Long paymentId) {
    List<Long> ids =
        jdbcTemplate.queryForList(
            """
            select id
              from payment_attempts
             where payment_id = ?
               and provider_command_id is not null
               and status in ('CREATED', 'PENDING')
             order by attempt_no desc
             limit 1
            """,
            Long.class,
            paymentId);
    return ids.isEmpty() ? null : ids.getFirst();
  }

  private PaymentStatus paymentStatus(Long paymentId) {
    String value =
        jdbcTemplate.queryForObject(
            "select status from payments where id = ?", String.class, paymentId);
    return PaymentStatus.valueOf(value);
  }

  private void advanceAfterDefinitiveFailure(Job job) {
    String nextStage = nextStageAfterFailure(job.stage());
    Instant next = nextBoundaryAfterFailure(job.stage(), job.graceEndsAt());
    int count = attemptCountForStage(nextStage);
    release(job, next, count, nextStage);
  }

  static String nextStageAfterFailure(String currentStage) {
    return switch (currentStage) {
      case DUE_FAILED -> D2_FAILED;
      case D2_FAILED -> D5_FAILED;
      case D5_FAILED -> D5_FAILED;
      default -> throw new IllegalStateException("Unknown dunning stage: " + currentStage);
    };
  }

  static Instant nextBoundaryAfterFailure(String currentStage, Instant graceEndsAt) {
    return switch (currentStage) {
      case DUE_FAILED -> graceEndsAt.minus(Duration.ofDays(2)); // D+5
      case D2_FAILED, D5_FAILED -> graceEndsAt; // D+7
      default -> throw new IllegalStateException("Unknown dunning stage: " + currentStage);
    };
  }

  static int attemptCountForStage(String stage) {
    return switch (stage) {
      case DUE_FAILED -> 1;
      case D2_FAILED -> 2;
      case D5_FAILED, GRACE_EXHAUSTED -> 3;
      default -> throw new IllegalStateException("Unknown dunning stage: " + stage);
    };
  }

  private void releaseObservation(Job job, Instant now) {
    Instant candidate = now.plus(OBSERVATION_DELAY);
    Instant next = candidate.isBefore(job.graceEndsAt()) ? candidate : job.graceEndsAt();
    release(job, next, job.attemptCount(), job.stage());
  }

  private void release(Job job, Instant next, int count, String stage) {
    jdbcTemplate.update(
        """
        update payment_dunning_jobs
           set status='ACTIVE',
               attempt_count=?,
               dunning_stage=?,
               next_attempt_at=?,
               claim_token=null,
               claimed_until=null,
               updated_at=now()
         where id=? and status='CLAIMED' and claim_token=?
        """,
        count,
        stage,
        Timestamp.from(next),
        job.id(),
        job.claimToken());
  }

  private void finish(Job job, String status, Instant next, int count, String stage) {
    jdbcTemplate.update(
        """
        update payment_dunning_jobs
           set status=?,
               attempt_count=?,
               dunning_stage=?,
               next_attempt_at=?,
               claim_token=null,
               claimed_until=null,
               updated_at=now()
         where id=? and status='CLAIMED' and claim_token=?
        """,
        status,
        count,
        stage,
        next == null ? null : Timestamp.from(next),
        job.id(),
        job.claimToken());
  }

  public void complete(Long paymentId) {
    jdbcTemplate.update(
        """
        update payment_dunning_jobs
           set status='RECOVERED', next_attempt_at=null,
               claim_token=null, claimed_until=null, updated_at=now()
         where payment_id=? and status in ('ACTIVE','CLAIMED')
        """,
        paymentId);
  }

  record Job(
      Long id,
      Long subscriptionId,
      Long paymentId,
      int attemptCount,
      String stage,
      Instant graceEndsAt,
      String claimToken) {}
}
