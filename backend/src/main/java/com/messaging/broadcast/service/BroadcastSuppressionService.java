package com.messaging.broadcast.service;

import com.messaging.broadcast.enums.BroadcastSuppressionReason;
import com.messaging.channel.enums.ChannelType;
import com.messaging.common.id.IdGenerator;
import java.sql.Timestamp;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BroadcastSuppressionService {

  private final JdbcTemplate jdbcTemplate;

  @Transactional
  public void suppressHardBounce(
      Long companyId, String normalizedDestination, Long sourceRecipientId) {
    if (normalizedDestination == null || normalizedDestination.isBlank()) return;
    Instant now = Instant.now();
    jdbcTemplate.update(
        """
        insert into broadcast_suppressions (
          id, company_id, channel_type, normalized_destination, reason,
          source_recipient_id, active, created_at, updated_at
        ) values (?, ?, ?, ?, ?, ?, true, ?, ?)
        on conflict (company_id, channel_type, normalized_destination)
        do update set
          reason = excluded.reason,
          source_recipient_id = excluded.source_recipient_id,
          active = true,
          updated_at = excluded.updated_at
        """,
        IdGenerator.nextId(),
        companyId,
        ChannelType.EMAIL.name(),
        normalizedDestination,
        BroadcastSuppressionReason.HARD_BOUNCE.name(),
        sourceRecipientId,
        Timestamp.from(now),
        Timestamp.from(now));
  }
}
