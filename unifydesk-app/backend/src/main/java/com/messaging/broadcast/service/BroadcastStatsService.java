package com.messaging.broadcast.service;

import com.messaging.broadcast.dto.BroadcastStatsResponse;
import com.messaging.broadcast.entity.BroadcastStats;
import com.messaging.broadcast.enums.BroadcastRecipientStatus;
import com.messaging.broadcast.repository.BroadcastStatsRepository;
import java.sql.Timestamp;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BroadcastStatsService {

  private final BroadcastStatsRepository repository;
  private final JdbcTemplate jdbcTemplate;

  @Transactional
  public void addSnapshot(Long runId, long total, long pending, long skipped) {
    repository.addSnapshotCounts(runId, total, pending, skipped);
  }

  @Transactional
  public void transition(
      Long runId, BroadcastRecipientStatus from, BroadcastRecipientStatus to) {
    if (from == to) return;
    transitionCount(runId, from, to, 1);
  }

  @Transactional
  public void transitionCount(
      Long runId,
      BroadcastRecipientStatus from,
      BroadcastRecipientStatus to,
      long count) {
    if (count <= 0 || from == to) return;
    String fromColumn = column(from);
    String toColumn = column(to);
    jdbcTemplate.update(
        "update broadcast_stats set "
            + fromColumn
            + " = greatest("
            + fromColumn
            + " - ?, 0), "
            + toColumn
            + " = "
            + toColumn
            + " + ?, updated_at = ? where run_id = ?",
        count,
        count,
        Timestamp.from(Instant.now()),
        runId);
  }

  @Transactional(readOnly = true)
  public BroadcastStatsResponse get(Long runId) {
    BroadcastStats stats = repository.findByRunId(runId).orElse(null);
    return BroadcastStatsResponse.from(stats);
  }

  private String column(BroadcastRecipientStatus status) {
    return switch (status) {
      case PENDING, RETRY -> "pending_count";
      case PROCESSING -> "processing_count";
      case ACCEPTED_BY_PROVIDER -> "accepted_count";
      case FAILED -> "failed_count";
      case BOUNCED -> "bounced_count";
      case UNKNOWN -> "unknown_count";
      case SKIPPED -> "skipped_count";
    };
  }
}
