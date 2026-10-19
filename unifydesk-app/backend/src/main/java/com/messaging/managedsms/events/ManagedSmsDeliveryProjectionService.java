package com.messaging.managedsms.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.common.id.IdGenerator;
import com.messaging.contacts.consent.ConsentTypes.Channel;
import com.messaging.contacts.consent.ConsentTypes.PreferenceSource;
import com.messaging.contacts.consent.ConsentTypes.SuppressionReason;
import com.messaging.contacts.core.domain.ContactTypes.IdentityType;
import com.messaging.contacts.integration.TrustedProviderSuppressionBridge;
import com.messaging.managedsms.metering.ManagedSmsUsageService;
import com.messaging.managedsms.runtime.ManagedSmsRuntimeRepository;
import java.sql.Timestamp;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ManagedSmsDeliveryProjectionService {

  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper;
  private final ManagedSmsRuntimeRepository runtimeRepository;
  private final ManagedSmsUsageService usageService;
  private final ManagedSmsRecipientStateService recipientStateService;
  private final TrustedProviderSuppressionBridge contactSuppressionBridge;

  @Transactional
  public void apply(ManagedSmsProviderEvent event) {
    if (event.type() == ManagedSmsProviderEventType.UNKNOWN || event.eventAt() == null) return;

    ManagedSmsRuntimeRepository.CorrelationRuntime correlation =
        runtimeRepository
            .findCorrelation(event.correlationToken())
            .orElseThrow(() -> new IllegalStateException("Managed SMS correlation expired"));

    if (!event.providerAccountKey().equals(correlation.providerAccountKey())) {
      throw new IllegalStateException("Managed SMS provider account mismatch");
    }
    if (correlation.providerRequestId() != null
        && (event.providerRequestId() == null
            || !correlation.providerRequestId().equals(event.providerRequestId()))) {
      throw new IllegalStateException("Managed SMS provider request mismatch");
    }

    Timestamp timestamp = Timestamp.from(event.eventAt());
    jdbcTemplate.update(
        """
        insert into managed_sms_recipient_delivery (
          recipient_id, company_id, accepted_at, delivered_at, failed_at, rejected_at,
          last_provider_status, last_failure_code, last_failure_reason, last_event_id, updated_at
        )
        select r.id, r.company_id, now(), null, null, null, ?, null, null, ?, now()
          from broadcast_recipients r
         where r.company_id = ?
           and r.run_id = ?
           and r.id = ?
        on conflict (recipient_id) do update
          set accepted_at = coalesce(managed_sms_recipient_delivery.accepted_at, excluded.accepted_at),
              last_event_id = excluded.last_event_id,
              updated_at = now()
        """,
        event.type().name(),
        event.id(),
        correlation.companyId(),
        correlation.runId(),
        correlation.recipientId());

    switch (event.type()) {
      case DELIVERED ->
          jdbcTemplate.update(
              """
              update managed_sms_recipient_delivery
                 set delivered_at = coalesce(delivered_at, ?),
                     last_provider_status = 'DELIVERED',
                     last_failure_code = null,
                     last_failure_reason = null,
                     last_event_id = ?,
                     updated_at = now()
               where company_id = ?
                 and recipient_id = ?
              """,
              timestamp,
              event.id(),
              correlation.companyId(),
              correlation.recipientId());
      case FAILED ->
          jdbcTemplate.update(
              """
              update managed_sms_recipient_delivery
                 set failed_at = coalesce(failed_at, ?),
                     last_provider_status = case when delivered_at is null then 'FAILED' else last_provider_status end,
                     last_failure_code = case when delivered_at is null then 'PROVIDER_FAILED' else last_failure_code end,
                     last_failure_reason = case when delivered_at is null then ? else last_failure_reason end,
                     last_event_id = ?,
                     updated_at = now()
               where company_id = ?
                 and recipient_id = ?
              """,
              timestamp,
              limit(event.failureReason()),
              event.id(),
              correlation.companyId(),
              correlation.recipientId());
      case REJECTED ->
          jdbcTemplate.update(
              """
              update managed_sms_recipient_delivery
                 set rejected_at = coalesce(rejected_at, ?),
                     last_provider_status = case when delivered_at is null then 'REJECTED' else last_provider_status end,
                     last_failure_code = case when delivered_at is null then 'PROVIDER_REJECTED' else last_failure_code end,
                     last_failure_reason = case when delivered_at is null then ? else last_failure_reason end,
                     last_event_id = ?,
                     updated_at = now()
               where company_id = ?
                 and recipient_id = ?
              """,
              timestamp,
              limit(event.failureReason()),
              event.id(),
              correlation.companyId(),
              correlation.recipientId());
      case SENT ->
          jdbcTemplate.update(
              """
              update managed_sms_recipient_delivery
                 set last_provider_status = case
                       when delivered_at is null and failed_at is null and rejected_at is null
                         then 'SENT' else last_provider_status end,
                     last_event_id = ?,
                     updated_at = now()
               where company_id = ?
                 and recipient_id = ?
              """,
              event.id(),
              correlation.companyId(),
              correlation.recipientId());
      case UNKNOWN -> {
        return;
      }
    }

    convergeSuppression(event, correlation);

    recipientStateService.confirmAccepted(
        correlation.companyId(),
        correlation.runId(),
        correlation.recipientId(),
        event.providerRequestId());

    String category =
        jdbcTemplate.queryForObject(
            """
            select h.message_category
              from managed_sms_run_bindings rb
              join managed_sms_headers h
                on h.id = rb.header_id and h.company_id = rb.company_id
             where rb.run_id = ?
               and rb.company_id = ?
            """,
            String.class,
            correlation.runId(),
            correlation.companyId());
    usageService.commitLatest(
        correlation.companyId(),
        correlation.runId(),
        correlation.recipientId(),
        event.providerRequestId(),
        category,
        event.credit());
  }

  private void convergeSuppression(
      ManagedSmsProviderEvent event, ManagedSmsRuntimeRepository.CorrelationRuntime correlation) {
    if (event.type() != ManagedSmsProviderEventType.REJECTED || !isNdnc(event)) {
      return;
    }

    jdbcTemplate.update(
        """
        insert into broadcast_suppressions(
          id,company_id,channel_type,normalized_destination,reason,
          source_recipient_id,active,created_at,updated_at)
        select ?,r.company_id,'SMS',r.normalized_destination,'UNSUBSCRIBED',
               r.id,true,now(),now()
        from broadcast_recipients r
        where r.company_id=? and r.id=?
        on conflict (company_id,channel_type,normalized_destination)
        do update set
          active=true,
          reason=case
            when broadcast_suppressions.reason in ('COMPLAINT','UNSUBSCRIBED')
              then broadcast_suppressions.reason
            else excluded.reason
          end,
          source_recipient_id=excluded.source_recipient_id,
          updated_at=now()
        """,
        IdGenerator.nextId(),
        correlation.companyId(),
        correlation.recipientId());

    contactSuppressionBridge.suppressRecipient(
        correlation.companyId(),
        correlation.recipientId(),
        IdentityType.PHONE,
        Channel.SMS,
        SuppressionReason.DND,
        PreferenceSource.MANAGED_SMS_EVENT,
        (event.providerAccountKey().startsWith("metareach:") ? "metareach:" : "msg91:")
            + event.eventKey(),
        event.eventAt(),
        "managed-sms:" + event.eventKey());
  }

  private boolean isNdnc(ManagedSmsProviderEvent event) {
    try {
      JsonNode payload = objectMapper.readTree(event.payloadJson());
      String status =
          ManagedSmsEventInboxService.text(
              payload, "upperCaseEventName", "eventName", "event", "desc", "status");
      return status != null && "NDNC".equalsIgnoreCase(status.trim());
    } catch (Exception exception) {
      throw new IllegalStateException("Invalid managed SMS provider event payload", exception);
    }
  }

  private String limit(String value) {
    if (value == null || value.length() <= 1000) return value;
    return value.substring(0, 1000);
  }
}
