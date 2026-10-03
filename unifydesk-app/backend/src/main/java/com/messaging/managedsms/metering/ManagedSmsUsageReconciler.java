package com.messaging.managedsms.metering;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ManagedSmsUsageReconciler {

  private final JdbcTemplate jdbcTemplate;
  private final ManagedSmsUsageService usageService;

  @Scheduled(fixedDelayString = "${app.managed-sms.usage-reconcile-ms:30000}")
  public void reconcile() {
    List<Row> rows =
        jdbcTemplate.query(
            """
            select u.company_id, u.run_id, u.recipient_id, u.attempt_no,
                   r.provider_message_id, r.accepted_at, h.message_category, u.provider_credit_snapshot
              from managed_sms_usage_reservations u
              join broadcast_recipients r
                on r.id = u.recipient_id and r.company_id = u.company_id
              join managed_sms_run_bindings rb
                on rb.run_id = u.run_id and rb.company_id = u.company_id
              join managed_sms_headers h
                on h.id = rb.header_id and h.company_id = rb.company_id
             where u.state in ('RESERVED', 'HELD_UNKNOWN')
               and r.status = 'ACCEPTED_BY_PROVIDER'
             order by u.updated_at
             limit 100
            """,
            (rs, rowNum) ->
                new Row(
                    rs.getLong("company_id"),
                    rs.getLong("run_id"),
                    rs.getLong("recipient_id"),
                    rs.getInt("attempt_no"),
                    rs.getString("provider_message_id"),
                    rs.getTimestamp("accepted_at") == null
                        ? null
                        : rs.getTimestamp("accepted_at").toInstant(),
                    rs.getString("message_category"),
                    rs.getBigDecimal("provider_credit_snapshot")));
    for (Row row : rows) {
      usageService.commit(
          row.companyId(),
          row.runId(),
          row.recipientId(),
          row.attemptNo(),
          row.providerMessageId(),
          row.messageCategory(),
          row.credit(),
          row.acceptedAt());
    }
  }

  private record Row(
      Long companyId,
      Long runId,
      Long recipientId,
      int attemptNo,
      String providerMessageId,
      Instant acceptedAt,
      String messageCategory,
      BigDecimal credit) {}
}
