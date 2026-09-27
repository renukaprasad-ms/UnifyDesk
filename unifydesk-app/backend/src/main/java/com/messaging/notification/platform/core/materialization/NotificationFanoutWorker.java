package com.messaging.notification.platform.core.materialization;

import com.messaging.notification.platform.core.config.NotificationPlatformProperties;
import com.messaging.notification.platform.core.materialization.NotificationMaterializer.MaterializationResult;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationFanoutWorker {

  private final JdbcTemplate jdbcTemplate;
  private final TransactionTemplate transactionTemplate;
  private final NotificationMaterializer materializer;
  private final NotificationPlatformProperties properties;

  @Scheduled(
      fixedDelayString = "${app.notification-platform.core.fanout.poll-interval-ms:750}",
      initialDelayString = "${app.notification-platform.core.fanout.poll-interval-ms:750}")
  public void runDue() {
    for (ClaimedJob job : claimDue()) {
      process(job);
    }
  }

  private List<ClaimedJob> claimDue() {
    return transactionTemplate.execute(
        status -> {
          Instant now = Instant.now();
          List<JobRow> rows =
              jdbcTemplate.query(
                  """
                  select id,event_id,company_id,cursor_user_id,batch_size,attempts
                  from notification_fanout_jobs
                  where state in ('PENDING','RUNNING','CLAIMED')
                    and next_attempt_at <= ?
                    and (claim_expires_at is null or claim_expires_at < ?)
                  order by next_attempt_at,id
                  for update skip locked
                  limit ?
                  """,
                  (rs, rowNum) ->
                      new JobRow(
                          rs.getLong("id"),
                          rs.getLong("event_id"),
                          rs.getLong("company_id"),
                          (Long) rs.getObject("cursor_user_id"),
                          rs.getInt("batch_size"),
                          rs.getInt("attempts")),
                  utc(now),
                  utc(now),
                  properties.getCore().getFanout().getClaimSize());
          return rows.stream()
              .map(
                  row -> {
                    String token = UUID.randomUUID().toString();
                    jdbcTemplate.update(
                        """
                        update notification_fanout_jobs
                        set state='CLAIMED',claim_token=?,claim_expires_at=?,updated_at=?
                        where id=?
                        """,
                        token,
                        utc(now.plus(properties.getCore().getFanout().getLease())),
                        utc(now),
                        row.id());
                    return new ClaimedJob(row, token);
                  })
              .toList();
        });
  }

  private void process(ClaimedJob claimed) {
    JobRow job = claimed.row();
    try {
      List<Long> recipients =
          jdbcTemplate.query(
              """
              select m.user_id
              from company_members m
              join users u on u.id=m.user_id
              where m.company_id=?
                and m.status='ACTIVE'
                and u.status='ACTIVE'
                and (? is null or m.user_id > ?)
              order by m.user_id
              limit ?
              """,
              (rs, rowNum) -> rs.getLong(1),
              job.companyId(),
              job.cursorUserId(),
              job.cursorUserId(),
              Math.max(50, Math.min(1000, job.batchSize())));

      if (recipients.isEmpty()) {
        transactionTemplate.executeWithoutResult(
            status -> {
              jdbcTemplate.update(
                  """
                  update notification_fanout_jobs
                  set state='COMPLETED',completed_at=now(),claim_token=null,claim_expires_at=null,
                      updated_at=now()
                  where id=? and claim_token=?
                  """,
                  job.id(),
                  claimed.token());
              jdbcTemplate.update(
                  """
                  update notification_events
                  set materialized_at=coalesce(materialized_at,now()),updated_at=now()
                  where id=?
                  """,
                  job.eventId());
            });
        return;
      }

      long processed = 0;
      long suppressed = 0;
      for (Long userId : recipients) {
        MaterializationResult result = materializer.materialize(job.eventId(), userId);
        if (result == MaterializationResult.SUPPRESSED) {
          suppressed++;
        }
        if (result != MaterializationResult.MISSING_EVENT) {
          processed++;
        }
      }

      long lastUserId = recipients.getLast();
      long processedFinal = processed;
      long suppressedFinal = suppressed;
      transactionTemplate.executeWithoutResult(
          status ->
              jdbcTemplate.update(
                  """
                  update notification_fanout_jobs
                  set state='RUNNING',cursor_user_id=?,processed_count=processed_count+?,
                      suppressed_count=suppressed_count+?,next_attempt_at=now(),
                      claim_token=null,claim_expires_at=null,last_error_code=null,updated_at=now()
                  where id=? and claim_token=?
                  """,
                  lastUserId,
                  processedFinal,
                  suppressedFinal,
                  job.id(),
                  claimed.token()));
    } catch (RuntimeException exception) {
      fail(claimed, exception);
    }
  }

  private void fail(ClaimedJob claimed, RuntimeException exception) {
    int attempt = claimed.row().attempts() + 1;
    boolean dead = attempt >= properties.getCore().getFanout().getMaxAttempts();
    Instant next = Instant.now().plus(backoff(attempt));
    jdbcTemplate.update(
        """
        update notification_fanout_jobs
        set state=?,attempts=attempts+1,next_attempt_at=?,claim_token=null,claim_expires_at=null,
            last_error_code='FANOUT_FAILED',updated_at=now()
        where id=? and claim_token=?
        """,
        dead ? "DEAD" : "PENDING",
        utc(next),
        claimed.row().id(),
        claimed.token());
    if (dead) {
      jdbcTemplate.update(
          "update notification_events set last_error_code='FANOUT_DEAD',updated_at=now() where id=?",
          claimed.row().eventId());
    }
    log.warn(
        "Notification fanout failed eventId={} attempt={}", claimed.row().eventId(), attempt);
  }

  private OffsetDateTime utc(Instant value) {
    return OffsetDateTime.ofInstant(value, ZoneOffset.UTC);
  }

  private Duration backoff(int attempt) {
    long initial = properties.getCore().getFanout().getRetryInitialBackoff().toMillis();
    long max = properties.getCore().getFanout().getRetryMaxBackoff().toMillis();
    int shift = Math.max(0, Math.min(20, attempt - 1));
    long value;
    try {
      value = Math.multiplyExact(initial, 1L << shift);
    } catch (ArithmeticException exception) {
      value = max;
    }
    return Duration.ofMillis(Math.min(max, value));
  }

  private record ClaimedJob(JobRow row, String token) {}

  private record JobRow(
      long id,
      long eventId,
      long companyId,
      Long cursorUserId,
      int batchSize,
      int attempts) {}
}
