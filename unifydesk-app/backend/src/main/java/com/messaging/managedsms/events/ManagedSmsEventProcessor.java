package com.messaging.managedsms.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
public class ManagedSmsEventProcessor {

  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper;
  private final ManagedSmsDeliveryProjectionService projectionService;
  private final PlatformTransactionManager transactionManager;

  @Scheduled(fixedDelayString = "${app.managed-sms.event-poll-ms:2000}")
  public void poll() {
    TransactionTemplate transaction = new TransactionTemplate(transactionManager);
    List<Long> eventIds = transaction.execute(status -> claimInternal(50));
    if (eventIds == null) return;

    for (Long eventId : eventIds) {
      try {
        transaction.executeWithoutResult(status -> processInternal(eventId));
      } catch (RuntimeException exception) {
        transaction.executeWithoutResult(
            status ->
                jdbcTemplate.update(
                    """
                    update managed_sms_provider_events
                       set processing_status = 'PENDING'
                     where id = ?
                       and processing_status = 'PROCESSING'
                    """,
                    eventId));
      }
    }
  }

  List<Long> claim(int limit) {
    TransactionTemplate transaction = new TransactionTemplate(transactionManager);
    List<Long> ids = transaction.execute(status -> claimInternal(limit));
    return ids == null ? List.of() : ids;
  }

  void process(Long eventId) {
    TransactionTemplate transaction = new TransactionTemplate(transactionManager);
    transaction.executeWithoutResult(status -> processInternal(eventId));
  }

  private List<Long> claimInternal(int limit) {
    List<Long> ids =
        jdbcTemplate.queryForList(
            """
            select id
              from managed_sms_provider_events
             where trust_status = 'TRUSTED'
               and processing_status = 'PENDING'
             order by received_at, id
             for update skip locked
             limit ?
            """,
            Long.class,
            limit);
    if (!ids.isEmpty()) {
      jdbcTemplate.batchUpdate(
          "update managed_sms_provider_events set processing_status = 'PROCESSING' where id = ?",
          ids,
          ids.size(),
          (statement, id) -> statement.setLong(1, id));
    }
    return ids;
  }

  private void processInternal(Long eventId) {
    ManagedSmsProviderEvent event =
        jdbcTemplate.queryForObject(
            """
            select id, provider_account_key, provider_event_key, event_type,
                   correlation_token, provider_request_id, provider_event_at, payload_json
              from managed_sms_provider_events
             where id = ?
               and trust_status = 'TRUSTED'
               and processing_status = 'PROCESSING'
             for update
            """,
            (rs, rowNum) -> {
              try {
                JsonNode payload = objectMapper.readTree(rs.getString("payload_json"));
                java.sql.Timestamp providerEventAt = rs.getTimestamp("provider_event_at");
                return new ManagedSmsProviderEvent(
                    rs.getLong("id"),
                    rs.getString("provider_account_key"),
                    rs.getString("provider_event_key"),
                    ManagedSmsProviderEventType.valueOf(rs.getString("event_type")),
                    rs.getString("correlation_token"),
                    rs.getString("provider_request_id"),
                    ManagedSmsEventInboxService.text(payload, "failureReason", "failedReason"),
                    ManagedSmsEventInboxService.decimal(payload, "credit"),
                    providerEventAt == null ? null : providerEventAt.toInstant(),
                    rs.getString("payload_json"));
              } catch (Exception exception) {
                throw new IllegalStateException("Invalid managed SMS provider event", exception);
              }
            },
            eventId);
    if (event == null) return;
    projectionService.apply(event);
    jdbcTemplate.update(
        """
        update managed_sms_provider_events
           set processing_status = 'PROCESSED', processed_at = now()
         where id = ?
        """,
        eventId);
  }
}
