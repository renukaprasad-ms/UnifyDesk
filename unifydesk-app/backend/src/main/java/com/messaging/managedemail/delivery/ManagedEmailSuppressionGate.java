package com.messaging.managedemail.delivery;

import com.messaging.broadcast.entity.BroadcastRecipient;
import com.messaging.broadcast.entity.BroadcastSuppression;
import com.messaging.broadcast.enums.BroadcastRecipientStatus;
import com.messaging.broadcast.repository.BroadcastRecipientRepository;
import com.messaging.broadcast.repository.BroadcastSuppressionRepository;
import com.messaging.broadcast.service.BroadcastStatsService;
import com.messaging.channel.enums.ChannelType;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ManagedEmailSuppressionGate {

  private final BroadcastSuppressionRepository suppressionRepository;
  private final BroadcastRecipientRepository recipientRepository;
  private final BroadcastStatsService statsService;

  @Transactional
  public boolean skipIfSuppressed(
      Long companyId, Long recipientId, String normalizedDestination) {
    Optional<BroadcastSuppression> suppression =
        suppressionRepository.findByCompanyIdAndChannelTypeAndNormalizedDestination(
            companyId, ChannelType.EMAIL, normalizedDestination);
    if (suppression.isEmpty() || !suppression.get().isActive()) {
      return false;
    }

    BroadcastRecipient recipient = recipientRepository.findByIdForUpdate(recipientId).orElse(null);
    if (recipient == null || !companyId.equals(recipient.getCompanyId())) {
      return true;
    }
    if (recipient.getStatus() == BroadcastRecipientStatus.SKIPPED) {
      return true;
    }
    if (recipient.getStatus() != BroadcastRecipientStatus.PROCESSING) {
      return true;
    }

    recipient.setStatus(BroadcastRecipientStatus.SKIPPED);
    recipient.setClaimedAt(null);
    recipient.setNextAttemptAt(null);
    recipient.setFailureCode("SUPPRESSED");
    recipient.setFailureReason("Recipient is currently suppressed");
    recipientRepository.save(recipient);
    statsService.transition(
        recipient.getRunId(),
        BroadcastRecipientStatus.PROCESSING,
        BroadcastRecipientStatus.SKIPPED);
    return true;
  }
}
