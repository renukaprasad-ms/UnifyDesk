package com.messaging.billing.payment;

import com.messaging.billing.service.SubscriptionLifecycleService;
import com.messaging.common.id.IdGenerator;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
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
  private final SubscriptionLifecycleService lifecycleService;

  public void schedule(
      Long companyId, Long subscriptionId, Long invoiceId, Long paymentId, Instant failedAt) {
    Instant base = failedAt == null ? Instant.now() : failedAt;
    jdbcTemplate.update(
        """
        insert into payment_dunning_jobs (
          id, company_id, subscription_id, invoice_id, payment_id, status,
          attempt_count, next_attempt_at, grace_ends_at, created_at, updated_at
        ) values (?, ?, ?, ?, ?, 'ACTIVE', 1, ?, ?, now(), now())
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
    List<Job> jobs =
        jdbcTemplate.query(
            """
            select id, subscription_id, payment_id, attempt_count, grace_ends_at
              from payment_dunning_jobs
             where status = 'ACTIVE'
               and next_attempt_at <= now()
             order by next_attempt_at, id
             limit 50
            """,
            (rs, rowNum) ->
                new Job(
                    rs.getLong("id"),
                    rs.getLong("subscription_id"),
                    rs.getLong("payment_id"),
                    rs.getInt("attempt_count"),
                    rs.getTimestamp("grace_ends_at").toInstant()));

    Instant now = Instant.now();
    for (Job job : jobs) {
      if (!now.isBefore(job.graceEndsAt())) {
        lifecycleService.applyDueLifecycle(now);
        jdbcTemplate.update(
            "update payment_dunning_jobs set status='EXHAUSTED', next_attempt_at=null, updated_at=now() where id=?",
            job.id());
        continue;
      }

      reconciliationService.reconcile(job.paymentId());
      int nextCount = job.attemptCount() + 1;
      Instant next =
          nextCount == 2
              ? now.plus(Duration.ofDays(3))
              : job.graceEndsAt();
      jdbcTemplate.update(
          """
          update payment_dunning_jobs
             set attempt_count = ?, next_attempt_at = ?, updated_at = now()
           where id = ? and status = 'ACTIVE'
          """,
          nextCount,
          Timestamp.from(next),
          job.id());
    }
  }

  public void complete(Long paymentId) {
    jdbcTemplate.update(
        "update payment_dunning_jobs set status='RECOVERED', next_attempt_at=null, updated_at=now() where payment_id=? and status='ACTIVE'",
        paymentId);
  }

  private record Job(
      Long id, Long subscriptionId, Long paymentId, int attemptCount, Instant graceEndsAt) {}
}
