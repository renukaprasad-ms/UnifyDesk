package com.messaging.channel.meta.runtime;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
@RequiredArgsConstructor
public class MetaWhatsAppPendingOutcomeRecovery {

  private static final int BATCH_SIZE = 100;

  private final JdbcTemplate jdbcTemplate;
  private final MetaWhatsAppRuntimeProperties properties;
  private final MetaWhatsAppRealtimePublisher realtimePublisher;

  @Scheduled(
      fixedDelayString =
          "${app.channel.meta-whatsapp.runtime.pending-recovery-delay-ms:30000}")
  @Transactional
  public void recoverStalePending() {
    Instant cutoff = Instant.now().minus(effectiveTimeout());
    List<Row> rows =
        jdbcTemplate.query(
            """
            select r.id,r.company_id,r.connection_id,r.inbox_message_id,m.conversation_id
            from meta_whatsapp_message_runtime r
            join inbox_messages m on m.id=r.inbox_message_id
            where r.direction='OUTBOUND'
              and r.detailed_status='PENDING'
              and r.provider_message_id is null
              and r.created_at < ?
            order by r.created_at,r.id
            limit ?
            for update of r skip locked
            """,
            (rs, rowNum) ->
                new Row(
                    rs.getLong("id"),
                    rs.getLong("company_id"),
                    rs.getLong("connection_id"),
                    rs.getLong("inbox_message_id"),
                    rs.getLong("conversation_id")),
            Timestamp.from(cutoff),
            BATCH_SIZE);

    for (Row row : rows) {
      int updated =
          jdbcTemplate.update(
              """
              update meta_whatsapp_message_runtime
              set detailed_status='UNKNOWN',status_rank=5,updated_at=now()
              where id=? and detailed_status='PENDING' and provider_message_id is null
              """,
              row.id());
      if (updated == 1) scheduleRealtime(row);
    }
  }

  @Transactional
  public boolean recoverIfStale(
      long companyId, long connectionId, String clientRequestId) {
    Instant cutoff = Instant.now().minus(effectiveTimeout());
    List<Row> rows =
        jdbcTemplate.query(
            """
            select r.id,r.company_id,r.connection_id,r.inbox_message_id,m.conversation_id
            from meta_whatsapp_message_runtime r
            join inbox_messages m on m.id=r.inbox_message_id
            where r.company_id=? and r.connection_id=? and r.client_request_id=?
              and r.direction='OUTBOUND'
              and r.detailed_status='PENDING'
              and r.provider_message_id is null
              and r.created_at < ?
            for update of r
            """,
            (rs, rowNum) ->
                new Row(
                    rs.getLong("id"),
                    rs.getLong("company_id"),
                    rs.getLong("connection_id"),
                    rs.getLong("inbox_message_id"),
                    rs.getLong("conversation_id")),
            companyId,
            connectionId,
            clientRequestId,
            Timestamp.from(cutoff));
    if (rows.isEmpty()) return false;

    Row row = rows.getFirst();
    int updated =
        jdbcTemplate.update(
            """
            update meta_whatsapp_message_runtime
            set detailed_status='UNKNOWN',status_rank=5,updated_at=now()
            where id=? and detailed_status='PENDING' and provider_message_id is null
            """,
            row.id());
    if (updated == 1) scheduleRealtime(row);
    return updated == 1;
  }

  private Duration effectiveTimeout() {
    Duration configured = properties.getPendingOutcomeTimeout();
    return configured == null || configured.isZero() || configured.isNegative()
        ? Duration.ofMinutes(2)
        : configured;
  }

  private void scheduleRealtime(Row row) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            realtimePublisher.statusChanged(
                row.companyId(), row.connectionId(), row.conversationId(), row.inboxMessageId());
          }
        });
  }

  private record Row(
      long id,
      long companyId,
      long connectionId,
      long inboxMessageId,
      long conversationId) {}
}
