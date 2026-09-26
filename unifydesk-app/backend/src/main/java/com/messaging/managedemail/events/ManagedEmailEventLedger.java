package com.messaging.managedemail.events;

import com.messaging.common.id.IdGenerator;
import com.messaging.managedemail.runtime.ManagedEmailRuntimeRepository;
import java.sql.Timestamp;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ManagedEmailEventLedger {

  private final JdbcTemplate jdbcTemplate;

  public void ensureMatchedReceipt(
      ManagedEmailProviderEvent event,
      ManagedEmailRuntimeRepository.ManagedRecipientRuntime runtime) {
    jdbcTemplate.update(
        """
        insert into managed_email_provider_events
          (id, sns_message_id, normalized_event_key, company_id, tenant_id, domain_id,
           sender_id, run_id, recipient_id, provider_message_id, event_type,
           event_timestamp, bounce_type, diagnostic_code, correlation_status,
           payload_hash, received_at, processed_at)
        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'MATCHED', ?, now(), null)
        on conflict do nothing
        """,
        IdGenerator.nextId(),
        event.snsMessageId(),
        normalizedKey(event),
        runtime.companyId(),
        runtime.tenantId(),
        runtime.domainId(),
        runtime.senderId(),
        runtime.runId(),
        runtime.recipientId(),
        event.providerMessageId(),
        event.type().name(),
        Timestamp.from(event.eventTimestamp()),
        event.bounceType(),
        event.diagnosticCode(),
        event.payloadHash());

    List<ReceiptState> states =
        jdbcTemplate.query(
            """
            select payload_hash, processed_at
              from managed_email_provider_events
             where sns_message_id = ?
                or normalized_event_key = ?
             order by received_at asc
             limit 1
            """,
            (resultSet, rowNum) ->
                new ReceiptState(
                    resultSet.getString("payload_hash"),
                    resultSet.getTimestamp("processed_at") != null),
            event.snsMessageId(),
            normalizedKey(event));
    if (states.isEmpty()) {
      throw new IllegalStateException("Managed Email provider event receipt was not persisted");
    }
    if (!event.payloadHash().equals(states.getFirst().payloadHash())) {
      throw new IllegalArgumentException("SNS MessageId or normalized event key payload mismatch");
    }
  }

  public boolean lockForLocalProcessing(ManagedEmailProviderEvent event) {
    List<Boolean> states =
        jdbcTemplate.query(
            """
            select processed_at is not null
              from managed_email_provider_events
             where sns_message_id = ?
                or normalized_event_key = ?
             order by received_at asc
             for update
             limit 1
            """,
            (resultSet, rowNum) -> resultSet.getBoolean(1),
            event.snsMessageId(),
            normalizedKey(event));
    return !states.isEmpty() && !states.getFirst();
  }

  public void markProcessed(ManagedEmailProviderEvent event) {
    jdbcTemplate.update(
        """
        update managed_email_provider_events
           set processed_at = now()
         where (sns_message_id = ? or normalized_event_key = ?)
           and processed_at is null
        """,
        event.snsMessageId(),
        normalizedKey(event));
  }

  public boolean recordRejected(ManagedEmailProviderEvent event, String reason) {
    int inserted =
        jdbcTemplate.update(
            """
            insert into managed_email_provider_events
              (id, sns_message_id, normalized_event_key, provider_message_id, event_type,
               event_timestamp, bounce_type, diagnostic_code, correlation_status,
               payload_hash, received_at, processed_at)
            values (?, ?, ?, ?, ?, ?, ?, ?, 'REJECTED_SCOPE', ?, now(), now())
            on conflict do nothing
            """,
            IdGenerator.nextId(),
            event.snsMessageId(),
            normalizedKey(event) + ":rejected:" + safeReason(reason),
            event.providerMessageId(),
            event.type().name(),
            Timestamp.from(event.eventTimestamp()),
            event.bounceType(),
            event.diagnosticCode(),
            event.payloadHash());
    return inserted == 1;
  }

  public boolean recordSubscription(SnsEnvelope envelope) {
    int inserted =
        jdbcTemplate.update(
            """
            insert into managed_email_provider_events
              (id, sns_message_id, normalized_event_key, event_type, event_timestamp,
               correlation_status, payload_hash, received_at, processed_at)
            values (?, ?, ?, 'SUBSCRIPTION', ?, 'SUBSCRIPTION', ?, now(), now())
            on conflict do nothing
            """,
            IdGenerator.nextId(),
            envelope.messageId(),
            "sns:" + envelope.messageId() + ":subscription",
            Timestamp.from(envelope.timestamp()),
            envelope.messageId());
    return inserted == 1;
  }

  String normalizedKey(ManagedEmailProviderEvent event) {
    return event.providerMessageId()
        + ":"
        + event.type().name()
        + ":"
        + event.eventTimestamp().toEpochMilli()
        + ":"
        + event.recipientId();
  }

  private String safeReason(String reason) {
    if (reason == null || reason.isBlank()) return "unknown";
    String safe = reason.replaceAll("[^A-Za-z0-9_-]", "_");
    return safe.substring(0, Math.min(40, safe.length()));
  }

  private record ReceiptState(String payloadHash, boolean processed) {}
}
