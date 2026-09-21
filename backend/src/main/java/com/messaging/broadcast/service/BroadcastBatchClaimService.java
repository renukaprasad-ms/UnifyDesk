package com.messaging.broadcast.service;

import com.messaging.broadcast.entity.BroadcastBatch;
import com.messaging.broadcast.enums.BroadcastBatchStatus;
import com.messaging.broadcast.repository.BroadcastBatchRepository;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BroadcastBatchClaimService {

  private final BroadcastBatchRepository repository;

  @Transactional
  public List<Long> claim(int limit) {
    List<BroadcastBatch> batches =
        repository.findClaimableForUpdate(Instant.now(), Math.max(1, limit));
    Instant now = Instant.now();
    for (BroadcastBatch batch : batches) {
      batch.setStatus(BroadcastBatchStatus.PROCESSING);
      batch.setClaimedAt(now);
      batch.setAttemptCount(batch.getAttemptCount() + 1);
      batch.setLastError(null);
    }
    repository.saveAll(batches);
    return batches.stream().map(BroadcastBatch::getId).toList();
  }
}
