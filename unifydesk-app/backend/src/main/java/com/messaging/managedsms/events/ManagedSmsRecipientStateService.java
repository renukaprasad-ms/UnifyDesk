package com.messaging.managedsms.events;

import com.messaging.broadcast.enums.BroadcastRecipientStatus;
import com.messaging.broadcast.service.BroadcastStatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ManagedSmsRecipientStateService {

  private final JdbcTemplate jdbcTemplate;
  private final BroadcastStatsService statsService;

  @Transactional
  public void confirmAccepted(Long recipientId, Long runId, String providerRequestId) {
    String previous =
        jdbcTemplate.queryForObject(
            "select status from broadcast_recipients where id = ? for update",
            String.class,
            recipientId);
    if (!BroadcastRecipientStatus.UNKNOWN.name().equals(previous)) return;
    int updated =
        jdbcTemplate.update(
            """
            update broadcast_recipients
               set status = 'ACCEPTED_BY_PROVIDER',
                   provider_message_id = coalesce(provider_message_id, ?),
                   accepted_at = coalesce(accepted_at, now()),
                   failure_code = null,
                   failure_reason = null,
                   updated_at = now()
             where id = ?
               and status = 'UNKNOWN'
            """,
            providerRequestId,
            recipientId);
    if (updated == 1) {
      statsService.transition(
          runId,
          BroadcastRecipientStatus.UNKNOWN,
          BroadcastRecipientStatus.ACCEPTED_BY_PROVIDER);
    }
  }
}
