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
      process(job);
    }
  }

  private List<Job> claimDue() {
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
                Stage.valueOf(rs.getString("dunning_stage")),
                rs.getTimestamp("grace_ends_at").toInstant(),
                rs.getString("claim_token")),
        token,
        claimSeconds);
  }

  private void process(Job job) {
    Instant now = Instant.now();
    if (!now.isBefore(job.graceEndsAt())) {
      lifecycleService.applyDueLifecycle(now);
      finish(job, "EXHAUSTED", job.attemptCount());
      return;
    }

    try {
      reconciliationService.reconcile(job.paymentId());
      reconcileLatestRecurring(job.paymentId());

      if (paymentStatus(job.paymentId()) == PaymentStatus.SUCCEEDED) {
        finish(job, "RECOVERED", job.attemptCount());
        return;
      }

      if (hasUnresolvedRecurringAttempt(job.paymentId())) {
        release(job, job.stage(), nextObservation(now, job.graceEndsAt()), job.attemptCount());
        return;
      }

      // If a prior invocation already launched this stage's retry and crashed/returned ambiguous,
      // reconciliation above establishes its definitive failure before the stage advances.
      if (retryAlreadyLaunched(job)) {
        advanceAfterDefinitiveFailure(job, now, job.attemptCount());
        return;
      }

      int beforeCommands = countRecurringCommands(job.paymentId());
      try {
        recurringChargeService.charge(job.paymentId(), retryScheduleTime(job));
      } catch (RuntimeException chargeFailure) {
        int afterCommands = countRecurringCommands(job.paymentId());
        int count = job.attemptCount() + (afterCommands > beforeCommands ? 1 : 0);
        if (hasUnresolvedRecurringAttempt(job.paymentId()) || afterCommands == beforeCommands) {
          release(job, job.stage(), nextObservation(now, job.graceEndsAt()), count);
        } else {
          advanceAfterDefinitiveFailure(job, now, count);
        }
        return;
      }

      int afterCommands = countRecurringCommands(job.paymentId());
      int count = job.attemptCount() + (afterCommands > beforeCommands ? 1 : 0);

      if (paymentStatus(job.paymentId()) == PaymentStatus.SUCCEEEDED) {
        finish(job, "RECOVERED", count);
        return;
      }
      if (hasUnresolvedRecurringAttempt(job.paymentId()) {
        release(job, job.stage(), nextObservation(now, job.graceEndsAt()), count);
        return;
      }
      if (afterCommands > beforeCommands) {
        advanceAfterDefinitiveFailure(job, now, count);
        return;
      }

      release(job, job.stage(), nextObservation(now, job.graceEndsAt()), count);
    } catch (RuntimeException ignored) {
      release(
          job,
          job.stage(),
          nextObservation(now, job.graceEndsAt()),
          job.attemptCount());
    }
  }

  private boolean retryAlreadyLaunched(Job job) {
    return switch (job.stage()) {
      case DUE_FAILED -> job.attemptCount() >= 2;
      case D2_FAILED -> job.attemptCount() >= 3;
      case D5_FAILED -> true;
    };
  }

  private void advanceAfterDefinitiveFailure(Job job, Instant now, int count) {
    switch (job.stage()) {
      case DUE_FAILED ->
          release(job, Stage.D2_FAILED, notBefore(dayFive(job), now), count);
      case D2_FAILED ->
          release(job, Stage.D5_FAILED, job.graceEndsAt(), count);
      case D5_FAILED ->
          release(job, Stage.D5_FAILED, job.graceEndsAt(), count);
    }
  }

  private Instant retryScheduleTime(Job job) {
    return switch (job.stage()) {
      case DUE_FAILED -> job.graceEndsAt().minus(Duration.ofDays(5)); // D+2
      case D2_FAILED -> job.graceEndsAt().minus(Duration.ofDays(2)); // D+5
      case D5_FAILED -> job.graceEndsAt(); // no further financial attempt before D+7
    };
  }

  private Instant dayFive(Job job) {
    return job.graceEndsAt().minus(Duration.ofDays(2));
  }

  private Instant notBefore(Instant target, Instant now) {
    return target.isAfter(now) ? target : now;
  }

  private void reconcileLatestRecurring(Long paymentId) {
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
    if (!ids.isEmpty()) {
      try {
        recurringChargeService.reconcileAttempt(ids.getFirst());
      } catch (RuntimeException ignored) {
        // Keep the same durable command recoverable; no new provider identity is minted.
      }
    }
  }

  private boolean hasUnresolvedRecurringAttempt(Long paymentId) {
    Integer count =
        jdbcTemplate.queryForObject(
            """
            select count(*)
              from payment_attempts
             where payment_id = ?
               and provider_command_id is not null
               and status in ('CREATED', 'PENDING')
            """,
            Integer.class,
            paymentId);
    return count != null && count > 0;
  }

  private int countRecurringCommands(Long paymentId) {
    Integer count =
        jdbcTemplate.queryForObject(
            "select count(*) from payment_attempts where payment_id = ? and provider_command_id is not null",
            Integer.class,
            paymentId);
    return count == null ? 0 : count;
  }

  private PaymentStatus paymentStatus(Long paymentId) {
    String value =
        jdbcTemplate.queryForObject(
            "select status from payments where id = ?", String.class, paymentId);
    return PaymentStatus.valueOf(value);
  }

  private Instant nextObservation(Instant now, Instant graceEnd) {
    Instant candidate = now.plus(Duration.ofHours(1));
    return candidate.isBefore(graceEnd) ? candidate : graceEnd;
  }

  private void release(Job job, Stage stage, Instant next, int count) {
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
        stage.name(),
        Timestamp.from(next),
        job.id(),
        job.claimToken());
  }

  private void finish(Job job, String status, int count) {
    jdbcTemplate.update(
        """
        update payment_dunning_jobs
           set status=?,
               attempt_count=?,
               next_attempt_at=null,
               claim_token=null,
               claimed_until=null,
               updated_at=now()
         where id=? and status='CLAIMED' and claim_token=?
        """,
        status,
        count,
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

  enum Stage {
    DUE_FAILED,
    D2_FAILED,
    D5_FAILED
  }

  private record Job(
      Long id,
      Long subscriptionId,
      Long paymentId,
      int attemptCount,
      Stage stage,
      Instant graceEndsAt,
      String claimToken) {}
}
