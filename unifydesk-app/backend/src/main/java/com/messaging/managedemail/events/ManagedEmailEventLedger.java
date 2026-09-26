package com.messaging.managedemail.events;

import com.messaging.common.id.IdGenerator;
import com.messaging.managedemail.runtime.ManagedEmailRuntimeRepository;
import java.sql.Timestamp;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ManagedEmailEventLedger {

  private final JdbcTemplate jdbcTemplate;

  public boolean alreadyReceived(String snsMessageId) {
    Integer count =
        jdbcTemplate.queryForObject(
            "select count(*) from managed_email_provider_events where sns_message_id = ?",
            Integer.class,
            snsMessageId);
    return count != null && count > 0;
  }

  public boolean recordMatched(
      ManagedEmailProviderEvent event,
      ManagedEmailRuntimeRepository.ManagedRecipientRuntime runtime) {
    return insert(
        event,
        runtime.companyId(),
        runtime.tenantId(),
        runtime.domainId(),
        runtime.senderId(),
        runtime.runId(),
        runtime.recipientId(),
        "MATCHED",
        normalizedKey(event));
  }

  public boolean recordRejected(ManagedEmailProviderEvent event, String reason) {
    return insert(
        event,
        null,
        null,
        null,
        null,
        null,
        null,
        "REJECTED_SCOPE",
        normalizedKey(event) + ":rejected:" + safeReason(reason));
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

  private boolean insert(
      ManagedEmailProviderEvent event,
      Long companyId,
      Long tenantId,
      Long domainId,
      Long senderId,
      Long runId,
      Long recipientId,
      String correlationStatus,
      String normalizedKey) {
    int inserted =
        jdbcTemplate.update(
            """
            insert into managed_email_provider_events
              (id, sns_message_id, normalized_event_key, company_id, tenant_id, domain_id,
               sender_id, run_id, recipient_id, provider_message_id, event_type,
               event_timestamp, bounce_type, diagnostic_code, correlation_status,
               payload_hash, received_at, processed_at)
            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now(), now())
            on conflict do nothing
            """,
            IdGenerator.nextId(),
            event.snsMessageId(),
            normalizedKey,
            companyId,
            tenantId,
            domainId,
            senderId,
            runId,
            recipientId,
            event.providerMessageId(),
            event.type().name(),
            Timestamp.from(event.eventTimestamp()),
            event.bounceType(),
            event.diagnosticCode(),
            correlationStatus,
            event.payloadHash());
    return inserted == 1;
  }

  private String normalizedKey(ManagedEmailProviderEvent event) {
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
    return reason.replaceAll("[^A-Za-z0-9_-]", "_").substring(0, Math.min(40, reason.length()));
  }
}
