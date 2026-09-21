package com.messaging.broadcast.service;

import com.messaging.broadcast.config.BroadcastProperties;
import com.messaging.broadcast.entity.*;
import com.messaging.broadcast.enums.*;
import com.messaging.broadcast.repository.*;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BroadcastRecoveryService {

  private final BroadcastRecipientRepository recipientRepository;
  private final BroadcastDeliveryAttemptRepository attemptRepository;
  private final BroadcastBatchRepository batchRepository;
  private final BroadcastRunRepository runRepository;
  private final BroadcastStatsService statsService;
  private final BroadcastProperties properties;

  @Transactional
  public void recover() {
    Instant now = Instant.now();
    Instant staleBefore = now.minus(properties.getStaleClaimAfter());

    List<BroadcastRecipient> recipients =
        recipientRepository.findStaleForUpdate(staleBefore, 500);
    for (BroadcastRecipient recipient : recipients) {
      BroadcastRun run = runRepository.findById(recipient.getRunId()).orElse(null);
      BroadcastDeliveryAttempt latest =
          attemptRepository
              .findFirstByRecipientIdOrderByAttemptNoDesc(recipient.getId())
              .orElse(null);
      if (latest != null && latest.getStatus() == BroadcastAttemptStatus.PROCESSING) {
        recipient.setStatus(BroadcastRecipientStatus.UNKNOWN);
        recipient.setFailureCode("WORKER_LOST_AFTER_SEND_START");
        recipient.setFailureReason(
            "Worker stopped after provider delivery began; automatic retry was suppressed");
        latest.setStatus(BroadcastAttemptStatus.UNKNOWN);
        latest.setCompletedAt(now);
        latest.setErrorCode("WORKER_LOST_AFTER_SEND_START");
        latest.setErrorMessage(recipient.getFailureReason());
        attemptRepository.save(latest);
        statsService.transition(
            recipient.getRunId(),
            BroadcastRecipientStatus.PROCESSING,
            BroadcastRecipientStatus.UNKNOWN);
      } else if (run != null && run.getStatus() == BroadcastRunStatus.CANCELLED) {
        recipient.setStatus(BroadcastRecipientStatus.SKIPPED);
        recipient.setFailureCode("CANCELLED");
        recipient.setFailureReason("Broadcast cancelled before provider delivery began");
        recipient.setNextAttemptAt(null);
        statsService.transition(
            recipient.getRunId(),
            BroadcastRecipientStatus.PROCESSING,
            BroadcastRecipientStatus.SKIPPED);
      } else {
        recipient.setStatus(BroadcastRecipientStatus.RETRY);
        recipient.setNextAttemptAt(now);
        statsService.transition(
            recipient.getRunId(),
            BroadcastRecipientStatus.PROCESSING,
            BroadcastRecipientStatus.RETRY);
      }
      recipient.setClaimedAt(null);
      recipientRepository.save(recipient);
    }

    List<BroadcastBatch> batches = batchRepository.findStaleForUpdate(staleBefore, 100);
    for (BroadcastBatch batch : batches) {
      BroadcastRun run = runRepository.findById(batch.getRunId()).orElse(null);
      if (run != null && run.getStatus() == BroadcastRunStatus.CANCELLED) {
        batch.setStatus(BroadcastBatchStatus.CANCELLED);
        batch.setNextAttemptAt(null);
        batch.setLastError("Broadcast was cancelled");
      } else {
        batch.setStatus(BroadcastBatchStatus.RETRY);
        batch.setNextAttemptAt(now);
        batch.setLastError("Recovered stale worker claim");
      }
      batch.setClaimedAt(null);
    }
    batchRepository.saveAll(batches);
  }
}
