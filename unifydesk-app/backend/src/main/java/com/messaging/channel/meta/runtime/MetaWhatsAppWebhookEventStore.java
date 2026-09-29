package com.messaging.channel.meta.runtime;

import com.messaging.common.id.IdGenerator;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class MetaWhatsAppWebhookEventStore {
  private final JdbcTemplate jdbcTemplate;
  private final MetaWhatsAppRuntimeProperties properties;

  @Transactional
  public int persist(List<MetaWhatsAppWebhookNormalizer.NormalizedEvent> events) {
    int inserted = 0;
    Instant now = Instant.now();
    for (var event : events) {
      inserted +=
          jdbcTemplate.update(
              """
              insert into meta_whatsapp_webhook_events
                (id,event_key,provider_object_id,phone_number_id,provider_message_id,event_kind,
                 provider_event_at,received_at,payload_json,state,attempt_count,next_attempt_at)
              values (?,?,?,?,?,?,?, ?,cast(? as jsonb),'PENDING',0,?)
              on conflict (event_key) do nothing
              """,
              IdGenerator.nextId(),
              event.eventKey(),
              event.providerObjectId(),
              event.phoneNumberId(),
              event.providerMessageId(),
              event.eventKind(),
              Timestamp.from(event.providerEventAt()),
              Timestamp.from(now),
              event.payloadJson(),
              Timestamp.from(now));
    }
    return inserted;
  }

  @Transactional
  public List<ClaimedEvent> claimDue() {
    String claimToken = UUID.randomUUID().toString();
    Instant now = Instant.now();
    Instant leaseUntil = now.plus(properties.getWorkerLease());
    return jdbcTemplate.query(
        """
        update meta_whatsapp_webhook_events e
        set state='CLAIMED', claim_token=?, claim_expires_at=?, attempt_count=attempt_count+1
        where e.id in (
          select id
          from meta_whatsapp_webhook_events
          where (state in ('PENDING','FAILED_RETRYABLE')
                 or (state='CLAIMED' and claim_expires_at < ?))
            and next_attempt_at <= ?
          order by next_attempt_at,id
          for update skip locked
          limit ?
        )
        returning id,event_kind,phone_number_id,provider_message_id,provider_event_at,
                  payload_json::text,attempt_count,claim_token
        """,
        (rs, rowNum) ->
            new ClaimedEvent(
                rs.getLong("id"),
                rs.getString("event_kind"),
                rs.getString("phone_number_id"),
                rs.getString("provider_message_id"),
                rs.getTimestamp("provider_event_at").toInstant(),
                rs.getString("payload_json"),
                rs.getInt("attempt_count"),
                rs.getString("claim_token")),
        claimToken,
        Timestamp.from(leaseUntil),
        Timestamp.from(now),
        Timestamp.from(now),
        properties.getWorkerBatchSize());
  }

  public void markProcessed(ClaimedEvent event) {
    finish(event, "PROCESSED", null);
  }

  public void markIgnored(ClaimedEvent event, String code) {
    finish(event, "IGNORED", code);
  }

  public void fail(ClaimedEvent event, String code, boolean retryable) {
    if (!retryable || event.attemptCount() >= properties.getWorkerMaxAttempts()) {
      finish(event, "DEAD", code);
      return;
    }
    Duration delay = retryDelay(event.attemptCount());
    jdbcTemplate.update(
        """
        update meta_whatsapp_webhook_events
        set state='FAILED_RETRYABLE', next_attempt_at=?, claim_token=null, claim_expires_at=null,
            last_error_code=?
        where id=? and claim_token=?
        """,
        Timestamp.from(Instant.now().plus(delay)),
        bounded(code, 80),
        event.id(),
        event.claimToken());
  }

  public int purge() {
    Instant now = Instant.now();
    int processed =
        jdbcTemplate.update(
            """
            delete from meta_whatsapp_webhook_events
            where state in ('PROCESSED','IGNORED') and received_at < ?
            """,
            Timestamp.from(now.minus(properties.getProcessedRetention())));
    return processed
        + jdbcTemplate.update(
            "delete from meta_whatsapp_webhook_events where state='DEAD' and received_at < ?",
            Timestamp.from(now.minus(properties.getDeadRetention())));
  }

  private void finish(ClaimedEvent event, String state, String code) {
    jdbcTemplate.update(
        """
        update meta_whatsapp_webhook_events
        set state=?, processed_at=now(), claim_token=null, claim_expires_at=null,last_error_code=?
        where id=? and claim_token=?
        """,
        state,
        bounded(code, 80),
        event.id(),
        event.claimToken());
  }

  private Duration retryDelay(int attempt) {
    long base = Math.max(1, properties.getRetryBaseDelay().toMillis());
    long max = Math.max(base, properties.getRetryMaxDelay().toMillis());
    long multiplier = 1L << Math.min(Math.max(0, attempt - 1), 20);
    long millis;
    try {
      millis = Math.min(max, Math.multiplyExact(base, multiplier));
    } catch (ArithmeticException ignored) {
      millis = max;
    }
    return Duration.ofMillis(millis);
  }

  private String bounded(String value, int max) {
    if (value == null) return null;
    return value.length() <= max ? value : value.substring(0, max);
  }

  public record ClaimedEvent(
      long id,
      String eventKind,
      String phoneNumberId,
      String providerMessageId,
      Instant providerEventAt,
      String payloadJson,
      int attemptCount,
      String claimToken) {}
}
