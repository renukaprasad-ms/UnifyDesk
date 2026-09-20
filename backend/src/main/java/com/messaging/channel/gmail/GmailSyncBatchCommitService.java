package com.messaging.channel.gmail;

import com.messaging.asyncjob.service.AsyncJobService;
import com.messaging.channel.entity.ChannelConnection;
import com.messaging.channel.gmail.GmailApiClient.GmailMessage;
import com.messaging.channel.gmail.GmailSyncStateService.ClaimedBatch;
import com.messaging.channel.repository.ChannelConnectionRepository;
import com.messaging.common.exception.NotFoundException;
import java.util.List;
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

  @Transactional
  public boolean commitResults(
      ClaimedBatch batch,
      List<GmailMessage> successfulMessages,
      List<String> retryIds,
      int permanentFailures,
      String error) {
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
    boolean retry = !retryIds.isEmpty() && batch.attempt() < maxAttempts;

    if (retry) {
      boolean applied =
          stateService.retryBatch(
              batch.batchId(), retryIds, terminalDelta, permanentFailures, error);
      if (applied && terminalDelta > 0) {
        asyncJobService.incrementProgress(
            batch.jobId(),
            terminalDelta,
            "Saved "
                + successfulMessages.size()
                + " Gmail messages; retrying "
                + retryIds.size());
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

    if (applied && finalDelta > 0) {
      asyncJobService.incrementProgress(
          batch.jobId(),
          finalDelta,
          exhaustedFailures + permanentFailures > 0
              ? "Processed Gmail batch with failures"
              : "Saved " + successfulMessages.size() + " Gmail messages");
    }
    return false;
  }

  @Transactional
  public boolean failAttempt(ClaimedBatch batch, String error, boolean retryable) {
    int maxAttempts = Math.max(1, properties.getSyncBatchRetryMaxAttempts());
    if (retryable && batch.attempt() < maxAttempts) {
      return stateService.retryBatch(batch.batchId(), batch.messageIds(), 0, 0, error);
    }

    int failed = stateService.failRemainingBatch(batch.batchId(), error);
    if (failed > 0) {
      asyncJobService.incrementProgress(
          batch.jobId(), failed, "Gmail batch failed after " + batch.attempt() + " attempts");
    }
    return false;
  }
}
