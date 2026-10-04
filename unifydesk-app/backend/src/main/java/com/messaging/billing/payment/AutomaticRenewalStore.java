package com.messaging.billing.payment;

import com.messaging.billing.enums.PaymentStatus;
import com.messaging.billing.enums.SubscriptionStatus;
import com.messaging.common.exception.ConflictException;
import com.messaging.common.id.IdGenerator;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Durable, replica-safe local orchestration state for automatic paid renewals. */
@Service
@RequiredArgsConstructor
class AutomaticRenewalStore {

  private static final int BATCH_SIZE = 100;

  private final JdbcTemplate jdbcTemplate;

  @Transactional
  void discoverDue(Instant now) {
    List<Candidate> due =
        jdbcTemplate.query(
            """
            select id, company_id, current_period_end
              from company_subscriptions
             where status = 'ACTIVE'
               and payment_policy = 'CASHFREE'
               and cancel_at_period_end = false
               and current_period_end is not null
               and current_period_end <= ?
             order by current_period_end, id
             for update skip locked
             limit ?
            """,
            (rs, rowNum) ->
                new Candidate(
                    rs.getLong("company_id"),
                    rs.getLong("id"),
                    rs.getTimestamp("current_period_end").toInstant()),
            Timestamp.from(now),
            BATCH_SIZE);

    for (Candidate candidate : due) {
      jdbcTemplate.update(
          """
          insert into payment_renewal_jobs (
            id, company_id, subscription_id, due_boundary, status,
            attempt_count, next_attempt_at, created_at, updated_at
          ) values (?, ?, ?, ?, 'READY', 0, ?, now(), now())
          on conflict (subscription_id, due_boundary) do nothing
          """,
          IdGenerator.nextId(),
          candidate.companyId(),
          candidate.subscriptionId(),
          Timestamp.from(candidate.dueBoundary()),
          Timestamp.from(now));
    }
  }

  @Transactional
  List<Job> claimDue(Instant now, long leaseSeconds) {
    String token = UUID.randomUUID().toString();
    long lease = Math.max(30L, leaseSeconds);
    return jdbcTemplate.query(
        """
        with due as (
          select id
            from payment_renewal_jobs
           where next_attempt_at <= ?
             and (
               status in ('READY', 'RETRY')
               or (status = 'CLAIMED' and claimed_until < ?)
             )
           order by next_attempt_at, id
           for update skip locked
           limit ?
        )
        update payment_renewal_jobs j
           set status='CLAIMED',
               claim_token=?,
               claimed_until=now() + (? * interval '1 second'),
               updated_at=now()
          from due
         where j.id=due.id
        returning j.id, j.company_id, j.subscription_id, j.due_boundary,
                  j.invoice_id, j.payment_id, j.attempt_count, j.claim_token
        """,
        (rs, rowNum) ->
            new Job(
                rs.getLong("id"),
                rs.getLong("company_id"),
                rs.getLong("subscription_id"),
                rs.getTimestamp("due_boundary").toInstant(),
                nullableLong(rs, "invoice_id"),
                nullableLong(rs, "payment_id"),
                rs.getInt("attempt_count"),
                rs.getString("claim_token")),
        Timestamp.from(now),
        Timestamp.from(now),
        BATCH_SIZE,
        token,
        lease);
  }

  @Transactional(readOnly = true)
  SubscriptionSnapshot subscription(Job job) {
    List<SubscriptionSnapshot> rows =
        jdbcTemplate.query(
            """
            select status, current_period_end, cancel_at_period_end, payment_policy
              from company_subscriptions
             where id=? and company_id=?
            """,
            (rs, rowNum) ->
                new SubscriptionSnapshot(
                    SubscriptionStatus.valueOf(rs.getString("status")),
                    rs.getTimestamp("current_period_end") == null
                        ? null
                        : rs.getTimestamp("current_period_end").toInstant(),
                    rs.getBoolean("cancel_at_period_end"),
                    rs.getString("payment_policy")),
            job.subscriptionId(),
            job.companyId());
    return rows.isEmpty() ? null : rows.getFirst();
  }

  @Transactional
  void bindFinancialObjects(Job job, Long invoiceId, Long paymentId) {
    List<BoundFinancialObjects> rows =
        jdbcTemplate.query(
            """
            select invoice_id, payment_id
              from payment_renewal_jobs
             where id=? and status='CLAIMED' and claim_token=?
             for update
            """,
            (rs, rowNum) ->
                new BoundFinancialObjects(
                    nullableLong(rs, "invoice_id"), nullableLong(rs, "payment_id")),
            job.id(),
            job.claimToken());
    if (rows.size() != 1) {
      throw new ConflictException("Renewal job claim is no longer authoritative");
    }
    BoundFinancialObjects current = rows.getFirst();
    if ((current.invoiceId() != null && !current.invoiceId().equals(invoiceId))
        || (current.paymentId() != null && !current.paymentId().equals(paymentId))) {
      throw new ConflictException(
          "Renewal job is already bound to different immutable financial objects");
    }

    int updated =
        jdbcTemplate.update(
            """
            update payment_renewal_jobs
               set invoice_id=?,
                   payment_id=?,
                   updated_at=now()
             where id=? and status='CLAIMED' and claim_token=?
            """,
            invoiceId,
            paymentId,
            job.id(),
            job.claimToken());
    if (updated != 1) {
      throw new ConflictException("Renewal job claim was lost before financial binding");
    }
  }

  @Transactional(readOnly = true)
  boolean zeroDueBoundaryAlreadyApplied(Job job) {
    if (job.invoiceId() == null || job.paymentId() != null) {
      return false;
    }
    Integer count =
        jdbcTemplate.queryForObject(
            """
            select count(*)
              from invoices i
              join billing_periods bp on bp.id = i.billing_period_id
              join company_subscriptions s on s.id = bp.subscription_id
             where i.id = ?
               and i.company_id = ?
               and bp.subscription_id = ?
               and bp.start_at = ?
               and i.status = 'OPEN'
               and i.amount_due = 0
               and s.current_period_start = bp.start_at
               and s.current_period_end is not distinct from bp.end_at
               and s.plan_id = bp.plan_id
               and s.plan_price_id = bp.plan_price_id
            """,
            Integer.class,
            job.invoiceId(),
            job.companyId(),
            job.subscriptionId(),
            Timestamp.from(job.dueBoundary()));
    return count != null && count == 1;
  }

  @Transactional(readOnly = true)
  PaymentStatus paymentStatus(Long paymentId) {
    String value =
        jdbcTemplate.queryForObject(
            "select status from payments where id=?", String.class, paymentId);
    return value == null ? null : PaymentStatus.valueOf(value);
  }

  @Transactional(readOnly = true)
  Long latestUnresolvedRecurringAttempt(Long paymentId) {
    List<Long> ids =
        jdbcTemplate.queryForList(
            """
            select id
              from payment_attempts
             where payment_id=?
               and provider_command_id is not null
               and status in ('CREATED', 'PENDING')
             order by attempt_no desc
             limit 1
            """,
            Long.class,
            paymentId);
    return ids.isEmpty() ? null : ids.getFirst();
  }

  @Transactional(readOnly = true)
  boolean hasDunning(Long paymentId) {
    Integer count =
        jdbcTemplate.queryForObject(
            """
            select count(*)
              from payment_dunning_jobs
             where payment_id=?
               and status in ('ACTIVE', 'CLAIMED', 'EXHAUSTED')
            """,
            Integer.class,
            paymentId);
    return count != null && count > 0;
  }

  @Transactional
  void release(Job job, Instant nextAttemptAt, String errorCategory) {
    jdbcTemplate.update(
        """
        update payment_renewal_jobs
           set status='RETRY',
               attempt_count=attempt_count+1,
               next_attempt_at=?,
               last_error_category=?,
               claim_token=null,
               claimed_until=null,
               updated_at=now()
         where id=? and status='CLAIMED' and claim_token=?
        """,
        Timestamp.from(nextAttemptAt),
        errorCategory,
        job.id(),
        job.claimToken());
  }

  @Transactional
  void finish(Job job, String status, Long invoiceId, Long paymentId, String errorCategory) {
    jdbcTemplate.update(
        """
        update payment_renewal_jobs
           set status=?,
               invoice_id=coalesce(invoice_id, ?),
               payment_id=coalesce(payment_id, ?),
               attempt_count=attempt_count+1,
               next_attempt_at=null,
               last_error_category=?,
               claim_token=null,
               claimed_until=null,
               updated_at=now()
         where id=? and status='CLAIMED' and claim_token=?
        """,
        status,
        invoiceId,
        paymentId,
        errorCategory,
        job.id(),
        job.claimToken());
  }

  private Long nullableLong(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
    long value = rs.getLong(column);
    return rs.wasNull() ? null : value;
  }

  record Candidate(Long companyId, Long subscriptionId, Instant dueBoundary) {}

  record Job(
      Long id,
      Long companyId,
      Long subscriptionId,
      Instant dueBoundary,
      Long invoiceId,
      Long paymentId,
      int attemptCount,
      String claimToken) {}

  private record BoundFinancialObjects(Long invoiceId, Long paymentId) {}

  record SubscriptionSnapshot(
      SubscriptionStatus status,
      Instant currentPeriodEnd,
      boolean cancelAtPeriodEnd,
      String paymentPolicy) {}
}
