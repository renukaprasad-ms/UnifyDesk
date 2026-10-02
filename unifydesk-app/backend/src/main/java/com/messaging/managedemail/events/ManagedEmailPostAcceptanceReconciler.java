package com.messaging.managedemail.events;

import com.messaging.managedemail.runtime.ManagedEmailRuntimeRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ManagedEmailPostAcceptanceReconciler {

  private final JdbcTemplate jdbcTemplate;
  private final ManagedEmailRuntimeRepository runtimeRepository;
  private final ManagedEmailSuppressionFeedbackService suppressionService;

  public void reconcile(Long recipientId) {
    ManagedEmailRuntimeRepository.ManagedRecipientRuntime runtime =
        runtimeRepository.findRecipientRuntime(recipientId).orElse(null);
    if (runtime == null || !"ACCEPTED_BY_PROVIDER".equals(runtime.recipientStatus())) return;

    List<ManagedEmailProviderEvent> hardBounceEvents =
        jdbcTemplate.query(
            """
            select sns_message_id,
                   event_timestamp,
                   provider_message_id,
                   company_id,
                   run_id,
                   sender_id,
                   domain_id,
                   recipient_id,
                   bounce_type,
                   diagnostic_code,
                   payload_hash
              from managed_email_provider_events
             where recipient_id = ?
               and company_id = ?
               and event_type = 'BOUNCE'
               and upper(coalesce(bounce_type, '')) = 'PERMANENT'
               and correlation_status = 'MATCHED'
             order by event_timestamp asc
             limit 1
            """,
            (resultSet, rowNum) ->
                new ManagedEmailProviderEvent(
                    resultSet.getString("sns_message_id"),
                    ManagedEmailProviderEventType.BOUNCE,
                    resultSet.getTimestamp("event_timestamp").toInstant(),
                    resultSet.getString("provider_message_id"),
                    resultSet.getObject("company_id", Long.class),
                    resultSet.getObject("run_id", Long.class),
                    resultSet.getObject("sender_id", Long.class),
                    resultSet.getObject("domain_id", Long.class),
                    resultSet.getObject("recipient_id", Long.class),
                    null,
                    resultSet.getString("bounce_type"),
                    resultSet.getString("diagnostic_code"),
                    resultSet.getString("payload_hash")),
            recipientId,
            runtime.companyId());
    if (hardBounceEvents.isEmpty()) return;

    suppressionService.hardBounce(runtime, hardBounceEvents.getFirst());
  }
}
