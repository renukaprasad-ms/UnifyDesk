package com.messaging.broadcast.service;

import com.messaging.broadcast.entity.*;
import com.messaging.broadcast.enums.*;
import com.messaging.broadcast.repository.*;
import com.messaging.broadcast.transport.BroadcastEmailResult;
import com.messaging.common.exception.NotFoundException;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BroadcastDeliveryStateService {

  private final BroadcastRecipientRepository recipientRepository;
  private final BroadcastDeliveryAttemptRepository attemptRepository;
  private final BroadcastStatsService statsService;

  @Transactional
  public AttemptContext begin(Long recipientId, BroadcastSenderProvider provider) {
    BroadcastRecipient recipient =
        recipientRepository
            .findByIdForUpdate(recipientId)
            .orElseThrow(() -> new NotFoundException("Broadcast recipient not found"));
    if (recipient.getStatus() != BroadcastRecipientStatus.PROCESSING) {
      throw new IllegalStateException("Broadcast recipient is not claimed");
    }
    int attemptNo = recipient.getAttemptCount() + 1;
    recipient.setAttemptCount(attemptNo);
    recipientRepository.save(recipient);

    BroadcastDeliveryAttempt attempt = new BroadcastDeliveryAttempt();
    attempt.setRecipientId(recipientId);
    attempt.setRunId(recipient.getRunId());
    attempt.setAttemptNo(attemptNo);
    attempt.setProviderType(provider);
    attempt.setStatus(BroadcastAttemptStatus.PROCESSING);
    attempt = attemptRepository.save(attempt);
    return new AttemptContext(recipientId, recipient.getRunId(), attempt.getId(), attemptNo);
  }

  @Transactional
  public void releaseForRateLimit(Long recipientId, Instant nextAttemptAt) {
    BroadcastRecipient recipient =
        recipientRepository
            .findByIdForUpdate(recipientId)
            .orElseThrow(() -> new NotFoundException("Broadcast recipient not found"));
    if (recipient.getStatus() != BroadcastRecipientStatus.PROCESSING) return;
    recipient.setStatus(BroadcastRecipientStatus.RETRY);
    recipient.setClaimedAt(null);
    recipient.setNextAttemptAt(nextAttemptAt);
    recipientRepository.save(recipient);
    statsService.transition(
        recipient.getRunId(),
        BroadcastRecipientStatus.PROCESSING,
        BroadcastRecipientStatus.RETRY);
  }

  @Transactional
  public void skipProcessing(Long recipientId, String code, String reason) {
    BroadcastRecipient recipient =
        recipientRepository
            .findByIdForUpdate(recipientId)
            .orElseThrow(() -> new NotFoundException("Broadcast recipient not found"));
    if (recipient.getStatus() != BroadcastRecipientStatus.PROCESSING) return;
    recipient.setStatus(BroadcastRecipientStatus.SKIPPED);
    recipient.setClaimedAt(null);
    recipient.setNextAttemptAt(null);
    recipient.setFailureCode(trim(code));
    recipient.setFailureReason(limit(reason));
    recipientRepository.save(recipient);
    statsService.transition(
        recipient.getRunId(),
        BroadcastRecipientStatus.PROCESSING,
        BroadcastRecipientStatus.SKIPPED);
  }

  @Transactional
  public void accepted(AttemptContext context, BroadcastEmailResult result) {
    accepted(
        context,
        result.providerMessageId(),
        result.internetMessageId(),
        result.acceptedAt());
  }

  @Transactional
  public void accepted(
      AttemptContext context,
      String providerMessageId,
      String internetMessageId,
      Instant acceptedAt) {
    BroadcastRecipient recipient = requireProcessing(context.recipientId());
    recipient.setStatus(BroadcastRecipientStatus.ACCEPTED_BY_PROVIDER);
    String normalizedProviderMessageId = trim(providerMessageId);
    String normalizedInternetMessageId = trim(internetMessageId);
    if (normalizedProviderMessageId != null) {
      recipient.setProviderMessageId(normalizedProviderMessageId);
    }
    if (normalizedInternetMessageId != null) {
      recipient.setInternetMessageId(normalizedInternetMessageId);
    }
    recipient.setAcceptedAt(acceptedAt == null ? Instant.now() : acceptedAt);
    recipient.setClaimedAt(null);
    recipient.setFailureCode(null);
    recipient.setFailureReason(null);
    recipientRepository.save(recipient);
    completeAttempt(
        context.attemptId(),
        BroadcastAttemptStatus.ACCEPTED,
        providerMessageId,
        null,
        null);
    statsService.transition(
        context.runId(),
        BroadcastRecipientStatus.PROCESSING,
        BroadcastRecipientStatus.ACCEPTED_BY_PROVIDER);
  }

  @Transactional
  public void retry(
      AttemptContext context, String code, String reason, Instant nextAttemptAt) {
    BroadcastRecipient recipient = requireProcessing(context.recipientId());
    recipient.setStatus(BroadcastRecipientStatus.RETRY);
    recipient.setClaimedAt(null);
    recipient.setNextAttemptAt(nextAttemptAt);
    recipient.setFailureCode(trim(code));
    recipient.setFailureReason(limit(reason));
    recipientRepository.save(recipient);
    completeAttempt(
        context.attemptId(),
        BroadcastAttemptStatus.RETRYABLE_FAILURE,
        null,
        code,
        reason);
    statsService.transition(
        context.runId(), BroadcastRecipientStatus.PROCESSING, BroadcastRecipientStatus.RETRY);
  }

  @Transactional
  public void failed(AttemptContext context, String code, String reason) {
    BroadcastRecipient recipient = requireProcessing(context.recipientId());
    recipient.setStatus(BroadcastRecipientStatus.FAILED);
    recipient.setClaimedAt(null);
    recipient.setNextAttemptAt(null);
    recipient.setFailedAt(Instant.now());
    recipient.setFailureCode(trim(code));
    recipient.setFailureReason(limit(reason));
    recipientRepository.save(recipient);
    completeAttempt(
        context.attemptId(),
        BroadcastAttemptStatus.PERMANENT_FAILURE,
        null,
        code,
        reason);
    statsService.transition(
        context.runId(), BroadcastRecipientStatus.PROCESSING, BroadcastRecipientStatus.FAILED);
  }

  @Transactional
  public void unknown(AttemptContext context, String code, String reason) {
    BroadcastRecipient recipient = requireProcessing(context.recipientId());
    recipient.setStatus(BroadcastRecipientStatus.UNKNOWN);
    recipient.setClaimedAt(null);
    recipient.setNextAttemptAt(null);
    recipient.setFailureCode(trim(code));
    recipient.setFailureReason(limit(reason));
    recipientRepository.save(recipient);
    completeAttempt(
        context.attemptId(), BroadcastAttemptStatus.UNKNOWN, null, code, reason);
    statsService.transition(
        context.runId(), BroadcastRecipientStatus.PROCESSING, BroadcastRecipientStatus.UNKNOWN);
  }

  private BroadcastRecipient requireProcessing(Long recipientId) {
    BroadcastRecipient recipient =
        recipientRepository
            .findByIdForUpdate(recipientId)
            .orElseThrow(() -> new NotFoundException("Broadcast recipient not found"));
    if (recipient.getStatus() != BroadcastRecipientStatus.PROCESSING) {
      throw new IllegalStateException("Broadcast recipient is not processing");
    }
    return recipient;
  }

  private void completeAttempt(
      Long attemptId,
      BroadcastAttemptStatus status,
      String providerMessageId,
      String errorCode,
      String errorMessage) {
    BroadcastDeliveryAttempt attempt =
        attemptRepository
            .findById(attemptId)
            .orElseThrow(() -> new NotFoundException("Broadcast delivery attempt not found"));
    attempt.setStatus(status);
    attempt.setProviderMessageId(trim(providerMessageId));
    attempt.setCompletedAt(Instant.now());
    attempt.setErrorCode(trim(errorCode));
    attempt.setErrorMessage(limit(errorMessage));
    attemptRepository.save(attempt);
  }

  private String trim(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private String limit(String value) {
    if (value == null || value.length() <= 1000) return value;
    return value.substring(0, 1000);
  }

  public record AttemptContext(Long recipientId, Long runId, Long attemptId, int attemptNo) {}
}
