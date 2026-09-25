package com.messaging.channel.gmail;

import com.messaging.asyncjob.service.AsyncJobService;
import com.messaging.channel.entity.ChannelConnection;
import com.messaging.channel.gmail.GmailApiClient.GmailMessage;
import com.messaging.channel.gmail.GmailSyncStateService.ClaimedBatch;
import com.messaging.channel.repository.ChannelConnectionRepository;
import com.messaging.common.exception.NotFoundException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GmailSyncBatchCommitService {

  private final GmailSyncStateService stateService;
  private final GmailInboxPersistenceService persistenceService;
  private final GmailSyncPersistenceLock persistenceLock;
  private final ChannelConnectionRepository connectionRepository;
  private final AsyncJobService asyncJobService;
  private final GmailOAuthProperties properties;
  private final GmailQuotaPolicy quotaPolicy;

  @Transactional
  public boolean commitResults(
      ClaimedBatch batch,
      List<GmailMessage> successfulMessages,
      List<String> retryIds,
      int permanentFailures,
      String error,
      Instant retryNotBefore) {
    persistenceLock.lockConnection(batch.connectionId());

    ChannelConnection connection =
        connectionRepository
            .findByIdAndCompanyId(batch.connectionId(), batch.companyId())
            .orElseThrow(() -> new NotFoundException("Gmail connection not found"));

    if (!successfulMessages.isEmpty()) {
      persistenceService.persistBatch(connection, successfulMessages);
    }

    int terminalDelta = successfulMessages.size() + Math.max(0, permanentFailures);
    int maxAttempts = Math.max(1, properties.getSyncBatchRetryMaxAttempts());
    boolean retry =
        !retryIds.isEmpty()
            && (isRateLimit(error) || batch.attempt() < maxAttempts);

    if (retry) {
      Instant nextAttemptAt =
          laterOf(Instant.now().plus(retryDelay(batch.attempt())), retryNotBefore);
      boolean applied =
          stateService.retryBatch(
              batch.batchId(),
              retryIds,
              terminalDelta,
              permanentFailures,
              error,
              nextAttemptAt);
      if (applied) {
        asyncJobService.incrementProgress(
            batch.jobId(),
            terminalDelta,
            retryMessage(error, successfulMessages.size(), retryIds.size(), nextAttemptAt));
      }
      return true;
    }

    int exhaustedFailures = retryIds.size();
    int finalDelta = terminalDelta + exhaustedFailures;
    boolean applied =
        stateService.completeBatch(
            batch.batchId(),
            finalDelta,
            permanentFailures + exhaustedFailures,
            error);

    if (applied) {
      stateService.deferNextPendingBatch(batch.connectionId(), quotaPolicy.nextEligibility());
      if (finalDelta > 0) {
        asyncJobService.incrementProgress(
            batch.jobId(),
            finalDelta,
            exhaustedFailures + permanentFailures > 0
                ? "Processed Gmail batch with failures"
                : "Saved " + successfulMessages.size() + " Gmail messages");
      }
    }
    return false;
  }

  @Transactional
  public boolean failAttempt(ClaimedBatch batch, String error, boolean retryable) {
    return failAttempt(batch, error, retryable, null);
  }

  @Transactional
  public boolean failAttempt(
      ClaimedBatch batch, String error, boolean retryable, Instant retryNotBefore) {
    int maxAttempts = Math.max(1, properties.getSyncBatchRetryMaxAttempts());
    if (retryable && (isRateLimit(error) || batch.attempt() < maxAttempts)) {
      Instant nextAttemptAt =
          laterOf(Instant.now().plus(retryDelay(batch.attempt())), retryNotBefore);
      boolean applied =
          stateService.retryBatch(
              batch.batchId(),
              batch.messageIds(),
              0,
              0,
              error,
              nextAttemptAt);
      if (applied) {
        asyncJobService.incrementProgress(
            batch.jobId(), 0, retryMessage(error, 0, batch.messageIds().size(), nextAttemptAt));
      }
      return applied;
    }

    int failed = stateService.failRemainingBatch(batch.batchId(), error);
    if (failed > 0) {
      asyncJobService.incrementProgress(
          batch.jobId(), failed, "Gmail batch failed after " + batch.attempt() + " attempts");
    }
    return false;
  }

  public boolean deferForQuota(ClaimedBatch batch, Instant availableAt) {
    return stateService.deferForQuota(batch.batchId(), availableAt);
  }

  private String retryMessage(
      String reason, int saved, int retrying, Instant nextAttemptAt) {
    if (isRateLimit(reason)) {
      return "Syncing Gmail in background";
    }
    return "Temporary Gmail error; retrying automatically"
        + " (saved="
        + saved
        + ", retrying="
        + retrying
        + ", nextAttemptAt="
        + nextAttemptAt
        + ")";
  }

  private boolean isRateLimit(String reason) {
    if (reason == null || reason.isBlank()) {
      return false;
    }
    String normalized = reason.toLowerCase(java.util.Locale.ROOT);
    return normalized.contains("ratelimitexceeded")
        || normalized.contains("userratelimitexceeded")
        || normalized.contains("rate limit")
        || normalized.contains("rate limited");
  }

  private Instant laterOf(Instant first, Instant second) {
    if (second == null) {
      return first;
    }
    return second.isAfter(first) ? second : first;
  }

  private Duration retryDelay(int attempt) {
    long initialMs =
        Math.max(1000L, properties.getSyncBatchRetryInitialBackoff().toMillis());
    long maxMs =
        Math.max(initialMs, properties.getSyncBatchRetryMaxBackoff().toMillis());
    int exponent = Math.min(Math.max(0, attempt - 1), 20);
    long exponential;
    try {
      exponential = Math.multiplyExact(initialMs, 1L << exponent);
    } catch (ArithmeticException exception) {
      exponential = maxMs;
    }
    long jitterMs = ThreadLocalRandom.current().nextLong(0, 1001);
    return Duration.ofMillis(Math.min(maxMs, exponential + jitterMs));
  }
}
