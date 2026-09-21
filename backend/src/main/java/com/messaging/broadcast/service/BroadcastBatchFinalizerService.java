package com.messaging.broadcast.service;

import com.messaging.broadcast.entity.BroadcastBatch;
import com.messaging.broadcast.enums.*;
import com.messaging.broadcast.repository.*;
import com.messaging.common.exception.NotFoundException;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BroadcastBatchFinalizerService {

  private final BroadcastBatchRepository batchRepository;
  private final BroadcastRecipientRepository recipientRepository;
  private final BroadcastRunFinalizerService runFinalizerService;

  @Transactional
  public void finish(Long batchId) {
    BroadcastBatch batch =
        batchRepository
            .findById(batchId)
            .orElseThrow(() -> new NotFoundException("Broadcast batch not found"));

    long active =
        recipientRepository.countByBatchIdAndStatusIn(
            batchId,
            List.of(
                BroadcastRecipientStatus.PENDING,
                BroadcastRecipientStatus.PROCESSING,
                BroadcastRecipientStatus.RETRY));
    if (active == 0) {
      batch.setStatus(BroadcastBatchStatus.COMPLETED);
      batch.setProcessedCount(batch.getRecipientCount());
      batch.setClaimedAt(null);
      batch.setNextAttemptAt(null);
      batchRepository.save(batch);
      runFinalizerService.tryFinalize(batch.getRunId());
      return;
    }

    batch.setStatus(BroadcastBatchStatus.RETRY);
    batch.setClaimedAt(null);
    Instant next = recipientRepository.findNextRetryAt(batchId);
    batch.setNextAttemptAt(next == null ? Instant.now().plusSeconds(1) : next);
    batchRepository.save(batch);
  }
}
