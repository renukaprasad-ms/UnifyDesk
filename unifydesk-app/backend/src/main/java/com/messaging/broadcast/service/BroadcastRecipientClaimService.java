package com.messaging.broadcast.service;

import com.messaging.broadcast.entity.BroadcastRecipient;
import com.messaging.broadcast.enums.BroadcastRecipientStatus;
import com.messaging.broadcast.repository.BroadcastRecipientRepository;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BroadcastRecipientClaimService {

  private final BroadcastRecipientRepository repository;
  private final BroadcastStatsService statsService;

  @Transactional
  public List<BroadcastRecipient> claim(Long batchId, int limit) {
    List<BroadcastRecipient> recipients =
        repository.findClaimableForUpdate(batchId, Instant.now(), Math.max(1, limit));
    Instant now = Instant.now();
    for (BroadcastRecipient recipient : recipients) {
      BroadcastRecipientStatus previous = recipient.getStatus();
      recipient.setStatus(BroadcastRecipientStatus.PROCESSING);
      recipient.setClaimedAt(now);
      recipient.setNextAttemptAt(null);
      statsService.transition(
          recipient.getRunId(), previous, BroadcastRecipientStatus.PROCESSING);
    }
    repository.saveAll(recipients);
    return List.copyOf(recipients);
  }
}
