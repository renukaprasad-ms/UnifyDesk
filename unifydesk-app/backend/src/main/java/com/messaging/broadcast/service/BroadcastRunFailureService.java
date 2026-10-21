package com.messaging.broadcast.service;

import com.messaging.broadcast.entity.BroadcastMessage;
import com.messaging.broadcast.entity.BroadcastRun;
import com.messaging.broadcast.enums.BroadcastBatchStatus;
import com.messaging.broadcast.enums.BroadcastMessageStatus;
import com.messaging.broadcast.enums.BroadcastRecipientStatus;
import com.messaging.broadcast.enums.BroadcastRunStatus;
import com.messaging.broadcast.repository.BroadcastMessageRepository;
import com.messaging.broadcast.repository.BroadcastRunRepository;
import com.messaging.common.exception.NotFoundException;
import com.messaging.notification.platform.core.event.NotificationEventService;
import java.sql.Timestamp;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
public class BroadcastRunFailureService {

  private final BroadcastRunRepository runRepository;
  private final BroadcastMessageRepository messageRepository;
  private final BroadcastStatsService statsService;
  private final BroadcastProgressPublisher progressPublisher;
  private final JdbcTemplate jdbcTemplate;
  private final NotificationEventService notificationEventService;

  @Transactional
  public void fail(Long runId, String code, String reason) {
    BroadcastRun run =
        runRepository
            .findByIdForUpdate(runId)
            .orElseThrow(() -> new NotFoundException("Broadcast run not found"));
    if (isTerminal(run.getStatus())) return;

    Instant now = Instant.now();
    run.setStatus(BroadcastRunStatus.FAILED);
    run.setCompletedAt(now);
    runRepository.save(run);

    BroadcastMessage message =
        messageRepository
            .findById(run.getBroadcastMessageId())
            .orElseThrow(() -> new NotFoundException("Broadcast not found"));
    message.setStatus(BroadcastMessageStatus.FAILED);
    messageRepository.save(message);

    notificationEventService.recordBroadcastFailed(
        run.getId(),
        message.getId(),
        run.getCompanyId(),
        message.getChannelType().name(),
        run.getCompletedAt());

    Long changed =
        jdbcTemplate.queryForObject(
            """
            with changed as (
              update broadcast_recipients
              set status = 'FAILED',
                  failed_at = ?,
                  failure_code = ?,
                  failure_reason = ?,
                  claimed_at = null,
                  next_attempt_at = null,
                  updated_at = ?
              where run_id = ?
                and status in ('PENDING', 'RETRY')
              returning 1
            )
            select count(*) from changed
            """,
            Long.class,
            Timestamp.from(now),
            limit(code, 128),
            limit(reason, 1000),
            Timestamp.from(now),
            runId);
    long failedCount = changed == null ? 0 : changed;
    if (failedCount > 0) {
      statsService.transitionCount(
          runId,
          BroadcastRecipientStatus.PENDING,
          BroadcastRecipientStatus.FAILED,
          failedCount);
    }

    jdbcTemplate.update(
        """
        update broadcast_batches
        set status = ?,
            claimed_at = null,
            next_attempt_at = null,
            last_error = ?,
            updated_at = ?
        where run_id = ?
          and status in ('PENDING', 'RETRY', 'PROCESSING')
        """,
        BroadcastBatchStatus.FAILED.name(),
        limit(reason, 1000),
        Timestamp.from(now),
        runId);

    afterCommit(() -> progressPublisher.publish(runId, true));
  }

  private boolean isTerminal(BroadcastRunStatus status) {
    return status == BroadcastRunStatus.COMPLETED
        || status == BroadcastRunStatus.PARTIALLY_FAILED
        || status == BroadcastRunStatus.CANCELLED
        || status == BroadcastRunStatus.FAILED;
  }

  private String limit(String value, int max) {
    if (value == null || value.length() <= max) return value;
    return value.substring(0, max);
  }

  private void afterCommit(Runnable action) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      action.run();
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            action.run();
          }
        });
  }
}
