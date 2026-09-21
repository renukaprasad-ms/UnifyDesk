package com.messaging.broadcast.service;

import com.messaging.broadcast.config.BroadcastProperties;
import com.messaging.broadcast.entity.*;
import com.messaging.broadcast.enums.*;
import com.messaging.broadcast.repository.*;
import com.messaging.channel.entity.ChannelConnection;
import com.messaging.channel.enums.ChannelConnectionStatus;
import com.messaging.channel.enums.ChannelType;
import com.messaging.channel.repository.ChannelConnectionRepository;
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
  private final ChannelConnectionRepository connectionRepository;
  private final BroadcastRecipientClaimService recipientClaimService;
  private final BroadcastDeliveryStateService deliveryStateService;
  private final BroadcastDeliveryService deliveryService;
  private final BroadcastBatchFinalizerService finalizerService;
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

    ChannelConnection connection =
        connectionRepository
            .findByIdAndCompanyId(run.getConnectionId(), run.getCompanyId())
            .orElseThrow(() -> new NotFoundException("Broadcast email connection not found"));
    if (connection.getChannelType() != ChannelType.EMAIL
        || connection.getStatus() != ChannelConnectionStatus.ACTIVE) {
      throw new IllegalStateException("Broadcast email connection is not active");
    }

    int claimSize = Math.max(1, properties.getRecipientClaimSize());
    while (true) {
      BroadcastRun current = runRepository.findById(run.getId()).orElse(run);
      if (current.getStatus() != BroadcastRunStatus.RUNNING) {
        break;
      }

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
        deliveryService.deliver(run, connection, recipient);
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
