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
  private final BroadcastStatsService statsService;
  private final BroadcastProperties properties;

  @Transactional
  public void recover() {
    Instant now = Instant.now();
    Instant staleBefore = now.minus(properties.getStaleClaimAfter());

    List<BroadcastRecipient> recipients =
        recipientRepository.findStaleForUpdate(staleBefore, 500);
    for (BroadcastRecipient recipient : recipients) {
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
      batch.setStatus(BroadcastBatchStatus.RETRY);
      batch.setClaimedAt(null);
      batch.setNextAttemptAt(now);
      batch.setLastError("Recovered stale worker claim");
    }
    batchRepository.saveAll(batches);
  }
}
