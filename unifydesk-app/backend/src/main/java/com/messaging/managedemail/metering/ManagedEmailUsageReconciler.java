package com.messaging.managedemail.metering;

import com.messaging.managedemail.runtime.ManagedEmailRuntimeProperties;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ManagedEmailUsageReconciler {

  private final JdbcTemplate jdbcTemplate;
  private final ManagedEmailUsageService usageService;
  private final ManagedEmailRuntimeProperties properties;

  public int reconcileStale(int limit) {
    if (limit <= 0) return 0;
    Instant staleBefore =
        Instant.now().minusSeconds(Math.max(1, properties.getStaleReservationMinutes()) * 60L);
    List<Reservation> reservations =
        jdbcTemplate.query(
            """
            select event.company_id,
                   event.recipient_id,
                   event.run_id,
                   event.idempotency_key,
                   recipient.status,
                   recipient.provider_message_id,
                   recipient.accepted_at
              from managed_email_usage_events event
              join broadcast_recipients recipient
                on recipient.id = event.recipient_id
             where event.event_type = 'RESERVE'
               and event.created_at < ?
               and not exists (
                 select 1
                   from managed_email_usage_events finished
                  where finished.recipient_id = event.recipient_id
                    and (
                      finished.event_type = 'COMMIT'
                      or finished.idempotency_key =
                         replace(event.idempotency_key, ':reserve:', ':release:')
                    )
               )
             order by event.created_at asc
             limit ?
            """,
            (resultSet, rowNum) ->
                new Reservation(
                    resultSet.getLong("company_id"),
                    resultSet.getLong("recipient_id"),
                    resultSet.getLong("run_id"),
                    resultSet.getString("idempotency_key"),
                    resultSet.getString("status"),
                    resultSet.getString("provider_message_id"),
                    resultSet.getTimestamp("accepted_at") == null
                        ? null
                        : resultSet.getTimestamp("accepted_at").toInstant()),
            java.sql.Timestamp.from(staleBefore),
            limit);

    int reconciled = 0;
    for (Reservation reservation : reservations) {
      Integer attemptNo = parseAttempt(reservation.key());
      if (attemptNo == null) continue;
      if ("ACCEPTED_BY_PROVIDER".equals(reservation.status())) {
        usageService.commit(
            reservation.companyId(),
            reservation.recipientId(),
            reservation.runId(),
            attemptNo,
            reservation.providerMessageId(),
            reservation.acceptedAt());
        reconciled++;
      } else if ("FAILED".equals(reservation.status())
          || "SKIPPED".equals(reservation.status())) {
        usageService.release(
            reservation.companyId(),
            reservation.recipientId(),
            reservation.runId(),
            attemptNo);
        reconciled++;
      }
    }
    return reconciled;
  }

  private Integer parseAttempt(String key) {
    int index = key.lastIndexOf(':');
    try {
      return index < 0 ? null : Integer.parseInt(key.substring(index + 1));
    } catch (NumberFormatException exception) {
      return null;
    }
  }

  private record Reservation(
      Long companyId,
      Long recipientId,
      Long runId,
      String key,
      String status,
      String providerMessageId,
      Instant acceptedAt) {}
}
