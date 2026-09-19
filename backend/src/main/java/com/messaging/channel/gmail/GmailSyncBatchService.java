package com.messaging.channel.gmail;

import com.messaging.channel.entity.ChannelConnection;
import com.messaging.channel.gmail.GmailApiClient.BatchMessageResult;
import com.messaging.channel.gmail.GmailApiClient.GmailMessage;
import com.messaging.channel.gmail.GmailSyncStateService.ClaimedBatch;
import com.messaging.channel.repository.ChannelConnectionRepository;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.NotFoundException;
import com.messaging.common.exception.ServiceUnavailableException;
import com.messaging.common.exception.TooManyRequestsException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class GmailSyncBatchService {

  private final GmailSyncStateService stateService;
  private final GmailSyncBatchCommitService commitService;
  private final GmailSyncFinalizerService finalizerService;
  private final GmailApiClient gmailApiClient;
  private final GmailTokenService tokenService;
  private final ChannelConnectionRepository connectionRepository;

  public void process(Long batchId, Long jobId, Long connectionId) {
    var claimed = stateService.claimBatch(batchId);
    if (claimed.isEmpty()) {
      finalizerService.tryFinalize(jobId);
      return;
    }

    ClaimedBatch batch = claimed.get();
    if (!batch.jobId().equals(jobId) || !batch.connectionId().equals(connectionId)) {
      commitService.failAttempt(batch, "Gmail batch identity mismatch", false);
      finalizerService.tryFinalize(jobId);
      return;
    }

    processClaimed(batch);
  }

  public void processClaimed(ClaimedBatch batch) {
    long startedNanos = System.nanoTime();
    long fetchMs = 0;
    long persistMs = 0;

    try {
      ChannelConnection connection =
          connectionRepository
              .findByIdAndCompanyId(batch.connectionId(), batch.companyId())
              .orElseThrow(() -> new NotFoundException("Gmail connection not found"));

      String accessToken = tokenService.accessToken(connection);

      long fetchStartedNanos = System.nanoTime();
      List<BatchMessageResult> results =
          gmailApiClient.batchMessages(accessToken, batch.messageIds());
      fetchMs = elapsedMillis(fetchStartedNanos);

      List<GmailMessage> successful = new ArrayList<>(results.size());
      List<String> retryIds = new ArrayList<>();
      int permanentFailures = 0;
      String lastError = null;

      for (BatchMessageResult result : results) {
        if (result.successful()) {
          successful.add(result.message());
        } else if (result.retryable()) {
          retryIds.add(result.messageId());
          lastError = result.reason();
        } else {
          permanentFailures++;
          lastError = result.reason();
        }
      }

      long persistStartedNanos = System.nanoTime();
      boolean retry =
          commitService.commitResults(
              batch, successful, retryIds, permanentFailures, lastError);
      persistMs = elapsedMillis(persistStartedNanos);

      log.info(
          "gmail_sync_batch_processed batchId={} jobId={} connectionId={} requested={} successful={} retry={} permanentFailures={} fetchMs={} persistMs={} totalMs={}",
          batch.batchId(),
          batch.jobId(),
          batch.connectionId(),
          batch.messageIds().size(),
          successful.size(),
          retryIds.size(),
          permanentFailures,
          fetchMs,
          persistMs,
          elapsedMillis(startedNanos));

      if (!retry) {
        finalizerService.tryFinalize(batch.jobId());
      }
    } catch (TooManyRequestsException | ServiceUnavailableException exception) {
      boolean retry = commitService.failAttempt(batch, exception.getMessage(), true);
      log.warn(
          "gmail_sync_batch_retry batchId={} jobId={} connectionId={} attempt={} fetchMs={} persistMs={} reason={}",
          batch.batchId(),
          batch.jobId(),
          batch.connectionId(),
          batch.attempt(),
          fetchMs,
          persistMs,
          exception.getClass().getSimpleName());
      if (!retry) {
        finalizerService.tryFinalize(batch.jobId());
      }
    } catch (BadRequestException | NotFoundException exception) {
      commitService.failAttempt(batch, exception.getMessage(), false);
      log.warn(
          "gmail_sync_batch_failed batchId={} jobId={} connectionId={} attempt={} reason={}",
          batch.batchId(),
          batch.jobId(),
          batch.connectionId(),
          batch.attempt(),
          exception.getMessage());
      finalizerService.tryFinalize(batch.jobId());
    } catch (RuntimeException exception) {
      boolean retry =
          commitService.failAttempt(batch, "Temporary Gmail batch processing failure", true);
      log.warn(
          "gmail_sync_batch_error batchId={} jobId={} connectionId={} attempt={} retry={} totalMs={} error={}",
          batch.batchId(),
          batch.jobId(),
          batch.connectionId(),
          batch.attempt(),
          retry,
          elapsedMillis(startedNanos),
          exception.getClass().getSimpleName());
      if (!retry) {
        finalizerService.tryFinalize(batch.jobId());
      }
    }
  }

  private long elapsedMillis(long startedNanos) {
    return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);
  }
}
