package com.messaging.broadcast.service;

import com.messaging.broadcast.dto.BroadcastStatsResponse;
import com.messaging.broadcast.entity.*;
import com.messaging.broadcast.enums.*;
import com.messaging.broadcast.repository.*;
import com.messaging.channel.email.EmailSentMessageHandler;
import com.messaging.channel.entity.ChannelConnection;
import com.messaging.channel.enums.ProviderType;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
public class BroadcastSentMessageHandler implements EmailSentMessageHandler {

  private final BroadcastRecipientRepository recipientRepository;
  private final BroadcastRunRepository runRepository;
  private final BroadcastMessageRepository messageRepository;
  private final BroadcastStatsService statsService;
  private final BroadcastProgressPublisher progressPublisher;

  @Override
  @Transactional
  public boolean handle(
      ChannelConnection connection,
      ProviderType providerType,
      String correlationId,
      String providerMessageId,
      String internetMessageId,
      Instant sentAt) {
    if (correlationId == null || correlationId.isBlank()) return false;

    BroadcastRecipient recipient =
        recipientRepository.findByProviderCorrelationId(correlationId.trim()).orElse(null);
    if (recipient == null\n        || recipient.getConnectionId() == null\n        || !recipient.getConnectionId().equals(connection.getId())) return false;

    recipient.setProviderMessageId(trim(providerMessageId));
    if (internetMessageId != null && !internetMessageId.isBlank()) {
      recipient.setInternetMessageId(internetMessageId.trim());
    }

    boolean recoveredUnknown = recipient.getStatus() == BroadcastRecipientStatus.UNKNOWN;
    if (recoveredUnknown) {
      recipient.setStatus(BroadcastRecipientStatus.ACCEPTED_BY_PROVIDER);
      recipient.setAcceptedAt(sentAt == null ? Instant.now() : sentAt);
      recipient.setFailureCode(null);
      recipient.setFailureReason(null);
      statsService.transition(
          recipient.getRunId(),
          BroadcastRecipientStatus.UNKNOWN,
          BroadcastRecipientStatus.ACCEPTED_BY_PROVIDER);
    }
    recipientRepository.save(recipient);

    if (recoveredUnknown) reconcileTerminalState(recipient.getRunId());
    afterCommit(() -> progressPublisher.publish(recipient.getRunId(), recoveredUnknown));
    return true;
  }

  private void reconcileTerminalState(Long runId) {
    BroadcastRun run = runRepository.findById(runId).orElse(null);
    if (run == null || run.getStatus() != BroadcastRunStatus.PARTIALLY_FAILED) return;

    BroadcastStatsResponse stats = statsService.get(runId);
    if (stats.failed() == 0 && stats.unknown() == 0 && stats.bounced() == 0) {
      run.setStatus(BroadcastRunStatus.COMPLETED);
      runRepository.save(run);
      messageRepository
          .findById(run.getBroadcastMessageId())
          .ifPresent(
              message -> {
                message.setStatus(BroadcastMessageStatus.COMPLETED);
                messageRepository.save(message);
              });
    }
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

  private String trim(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
