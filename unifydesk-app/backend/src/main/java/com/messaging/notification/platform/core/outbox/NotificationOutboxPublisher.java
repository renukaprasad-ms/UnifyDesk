package com.messaging.notification.platform.core.outbox;

import com.messaging.kafka.event.KafkaEvent;
import com.messaging.kafka.key.KafkaKeyResolver;
import com.messaging.kafka.priority.MessagePriority;
import com.messaging.kafka.producer.EventPublisher;
import com.messaging.notification.platform.core.config.NotificationPlatformProperties;
import com.messaging.notification.platform.core.event.NotificationMaterializeRequested;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationOutboxPublisher {

  private final JdbcTemplate jdbcTemplate;
  private final TransactionTemplate transactionTemplate;
  private final EventPublisher eventPublisher;
  private final KafkaKeyResolver keyResolver;
  private final NotificationPlatformProperties properties;

  @Scheduled(
      fixedDelayString = "${app.notification-platform.core.outbox.poll-interval-ms:750}",
      initialDelayString = "${app.notification-platform.core.outbox.poll-interval-ms:750}")
  public void publishDue() {
    recoverStalePublished();
    for (ClaimedEvent event : claimDue()) {
      publish(event);
    }
  }

  private List<ClaimedEvent> claimDue() {
    return transactionTemplate.execute(
        status -> {
          Instant now = Instant.now();
          List<EventRow> rows =
              jdbcTemplate.query(
                  """
                  select id,event_key,event_type,schema_version,company_id,recipient_user_id,
                         correlation_id,causation_id,priority,occurred_at,publish_attempts
                  from notification_events
                  where (
                    (publish_state='PENDING' and next_publish_at <= ?)
                    or
                    (publish_state='CLAIMED' and claim_expires_at < ?)
                  )
                  order by next_publish_at,id
                  for update skip locked
                  limit ?
                  """,
                  (rs, rowNum) ->
                      new EventRow(
                          rs.getLong("id"),
                          rs.getString("event_key"),
                          rs.getString("event_type"),
                          rs.getInt("schema_version"),
                          (Long) rs.getObject("company_id"),
                          (Long) rs.getObject("recipient_user_id"),
                          rs.getString("correlation_id"),
                          rs.getString("causation_id"),
                          rs.getString("priority"),
                          rs.getTimestamp("occurred_at").toInstant(),
                          rs.getInt("publish_attempts")),
                  utc(now),
                  utc(now),
                  properties.getCore().getOutbox().getBatchSize());
          return rows.stream()
              .map(
                  row -> {
                    String token = UUID.randomUUID().toString();
                    jdbcTemplate.update(
                        """
                        update notification_events
                        set publish_state='CLAIMED', publish_attempts=publish_attempts+1,
                            claim_token=?, claim_expires_at=?, updated_at=?
                        where id=?
                        """,
                        token,
                        utc(now.plus(properties.getCore().getOutbox().getLease())),
                        utc(now),
                        row.id());
                    return new ClaimedEvent(row, token);
                  })
              .toList();
        });
  }

  private void publish(ClaimedEvent claimed) {
    EventRow row = claimed.row();
    KafkaEvent<NotificationMaterializeRequested> kafkaEvent =
        new KafkaEvent<>(
            Long.toString(row.id()),
            "notification.materialize.requested",
            "1",
            row.occurredAt(),
            row.companyId(),
            row.recipientUserId(),
            row.correlationId(),
            row.causationId(),
            MessagePriority.valueOf(row.priority()),
            new NotificationMaterializeRequested(
                Long.toString(row.id()), row.eventKey(), row.eventType(), row.schemaVersion()));
    try {
      eventPublisher
          .publishAsync(keyResolver.resolve(kafkaEvent), kafkaEvent)
          .get(properties.getCore().getOutbox().getPublishTimeout().toMillis(), TimeUnit.MILLISECONDS);
      jdbcTemplate.update(
          """
          update notification_events
          set publish_state='PUBLISHED', published_at=?,
              claim_token=null,claim_expires_at=null,last_error_code=null,updated_at=?
          where id=? and claim_token=?
          """,
          utc(Instant.now()),
          utc(Instant.now()),
          row.id(),
          claimed.token());
    } catch (Exception exception) {
      int attempt = row.publishAttempts() + 1;
      boolean dead = attempt >= properties.getCore().getOutbox().getMaxAttempts();
      Instant next = Instant.now().plus(backoff(attempt));
      jdbcTemplate.update(
          """
          update notification_events
          set publish_state=?,next_publish_at=?,claim_token=null,claim_expires_at=null,
              last_error_code='KAFKA_PUBLISH_FAILED',updated_at=?
          where id=? and claim_token=?
          """,
          dead ? "DEAD" : "PENDING",
          utc(next),
          utc(Instant.now()),
          row.id(),
          claimed.token());
      log.warn("Notification event publish failed eventId={} attempt={}", row.id(), attempt);
    }
  }

  private void recoverStalePublished() {
    Instant cutoff = Instant.now().minus(properties.getCore().getOutbox().getRecoveryAfter());
    jdbcTemplate.update(
        """
        update notification_events
        set publish_state='PENDING',next_publish_at=now(),updated_at=now()
        where id in (
          select e.id
          from notification_events e
          where e.publish_state='PUBLISHED'
            and e.materialized_at is null
            and e.published_at < ?
            and not exists (
              select 1 from notification_fanout_jobs j where j.event_id=e.id
            )
          order by e.published_at,e.id
          limit ?
        )
        """,
        utc(cutoff),
        properties.getCore().getOutbox().getBatchSize());
  }

  private OffsetDateTime utc(Instant value) {
    return OffsetDateTime.ofInstant(value, ZoneOffset.UTC);
  }

  private Duration backoff(int attempt) {
    long initial = properties.getCore().getOutbox().getRetryInitialBackoff().toMillis();
    long max = properties.getCore().getOutbox().getRetryMaxBackoff().toMillis();
    int shift = Math.max(0, Math.min(20, attempt - 1));
    long value;
    try {
      value = Math.multiplyExact(initial, 1L << shift);
    } catch (ArithmeticException exception) {
      value = max;
    }
    return Duration.ofMillis(Math.min(max, value));
  }

  private record ClaimedEvent(EventRow row, String token) {}

  private record EventRow(
      long id,
      String eventKey,
      String eventType,
      int schemaVersion,
      Long companyId,
      Long recipientUserId,
      String correlationId,
      String causationId,
      String priority,
      Instant occurredAt,
      int publishAttempts) {}
}
