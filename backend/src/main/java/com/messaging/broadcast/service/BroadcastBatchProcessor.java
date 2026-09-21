package com.messaging.broadcast.service;

import com.messaging.broadcast.config.BroadcastProperties;
import com.messaging.broadcast.entity.*;
import com.messaging.broadcast.enums.*;
import com.messaging.broadcast.repository.*;
import com.messaging.common.exception.NotFoundException;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BroadcastBatchProcessor {

  private final BroadcastBatchRepository batchRepository;
  private final BroadcastRunRepository runRepository;
  private final BroadcastSenderRepository senderRepository;
  private final BroadcastSenderConnectionRepository senderConnectionRepository;
  private final BroadcastRecipientClaimService recipientClaimService;
  private final BroadcastDeliveryStateService deliveryStateService;
  private final BroadcastDeliveryService deliveryService;
  private final BroadcastBatchFinalizerService finalizerService;
  private final BroadcastRunFailureService failureService;
  private final BroadcastProgressPublisher progressPublisher;
  private final BroadcastProperties properties;

  public void process(Long batchId) {
    BroadcastBatch batch =
        batchRepository
            .findById(batchId)
            .orElseThrow(() -> new NotFoundException("Broadcast batch not found"));
    BroadcastRun run =
        runRepository
            .findById(batch.getRunId())
            .orElseThrow(() -> new NotFoundException("Broadcast run not found"));

    if (run.getStatus() != BroadcastRunStatus.RUNNING) {
      releaseBatch(batch);
      return;
    }
    if (run.getSenderId() == null) {
      failureService.fail(
          run.getId(), "SENDER_UNAVAILABLE", "Legacy mailbox broadcasts can no longer execute");
      return;
    }

    BroadcastSender sender =
        senderRepository
            .findByIdAndCompanyId(run.getSenderId(), run.getCompanyId())
            .orElse(null);
    if (sender == null || sender.getStatus() != BroadcastSenderStatus.ACTIVE) {
      failureService.fail(
          run.getId(), "SENDER_UNAVAILABLE", "Broadcast sender is missing or no longer active");
      return;
    }

    BroadcastSenderConnection connection =
        senderConnectionRepository
            .findByIdAndCompanyId(sender.getConnectionId(), run.getCompanyId())
            .orElse(null);
    if (connection == null
        || connection.getStatus() != BroadcastSenderConnectionStatus.ACTIVE
        || !connection.isSendingEnabled()) {
      failureService.fail(
          run.getId(),
          "SENDER_UNAVAILABLE",
          "Broadcast sender connection is missing or no longer active");
      return;
    }

    int claimSize = Math.max(1, properties.getRecipientClaimSize());
    while (true) {
      BroadcastRun current = runRepository.findById(run.getId()).orElse(run);
      if (current.getStatus() != BroadcastRunStatus.RUNNING) break;

      List<BroadcastRecipient> recipients =
          recipientClaimService.claim(batchId, claimSize);
      if (recipients.isEmpty()) break;

      for (BroadcastRecipient recipient : recipients) {
        BroadcastRun latest = runRepository.findById(run.getId()).orElse(run);
        if (latest.getStatus() != BroadcastRunStatus.RUNNING) {
          deliveryStateService.releaseForRateLimit(
              recipient.getId(), Instant.now().plusSeconds(1));
          continue;
        }
        deliveryService.deliver(run, connection, sender, recipient);
      }
    }

    finalizerService.finish(batchId);
    progressPublisher.publish(run.getId(), false);
  }

  private void releaseBatch(BroadcastBatch batch) {
    if (batch.getStatus() != BroadcastBatchStatus.PROCESSING) return;
    batch.setStatus(BroadcastBatchStatus.RETRY);
    batch.setClaimedAt(null);
    batch.setNextAttemptAt(Instant.now().plusSeconds(1));
    batchRepository.save(batch);
  }
}
