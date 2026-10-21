package com.messaging.broadcast.service;

import com.messaging.broadcast.dto.BroadcastStatsResponse;
import com.messaging.broadcast.entity.*;
import com.messaging.broadcast.enums.*;
import com.messaging.broadcast.repository.*;
import com.messaging.common.exception.NotFoundException;
import com.messaging.notification.platform.core.event.NotificationEventService;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
public class BroadcastRunFinalizerService {

  private final BroadcastRunRepository runRepository;
  private final BroadcastBatchRepository batchRepository;
  private final BroadcastMessageRepository messageRepository;
  private final BroadcastStatsService statsService;
  private final BroadcastProgressPublisher progressPublisher;
  private final NotificationEventService notificationEventService;

  @Transactional
  public void tryFinalize(Long runId) {
    BroadcastRun run =
        runRepository
            .findByIdForUpdate(runId)
            .orElseThrow(() -> new NotFoundException("Broadcast run not found"));
    if (run.getStatus() != BroadcastRunStatus.RUNNING || !run.isSnapshotComplete()) return;

    long active =
        batchRepository.countByRunIdAndStatusIn(
            runId,
            List.of(
                BroadcastBatchStatus.PENDING,
                BroadcastBatchStatus.PROCESSING,
                BroadcastBatchStatus.RETRY));
    if (active > 0) return;

    BroadcastStatsResponse stats = statsService.get(runId);
    boolean partial = stats.failed() > 0 || stats.unknown() > 0 || stats.bounced() > 0;
    run.setStatus(partial ? BroadcastRunStatus.PARTIALLY_FAILED : BroadcastRunStatus.COMPLETED);
    run.setCompletedAt(Instant.now());
    runRepository.save(run);

    BroadcastMessage message =
        messageRepository
            .findById(run.getBroadcastMessageId())
            .orElseThrow(() -> new NotFoundException("Broadcast not found"));
    message.setStatus(
        partial
            ? BroadcastMessageStatus.PARTIALLY_FAILED
            : BroadcastMessageStatus.COMPLETED);
    messageRepository.save(message);

    if (partial) {
      notificationEventService.recordBroadcastFailed(
          run.getId(),
          message.getId(),
          run.getCompanyId(),
          message.getChannelType().name(),
          run.getCompletedAt());
    } else {
      notificationEventService.recordBroadcastCompleted(
          run.getId(),
          message.getId(),
          run.getCompanyId(),
          message.getChannelType().name(),
          run.getCompletedAt());
    }

    afterCommit(() -> progressPublisher.publish(runId, true));
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
