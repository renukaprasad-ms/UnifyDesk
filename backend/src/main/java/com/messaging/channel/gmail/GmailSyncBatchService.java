package com.messaging.channel.gmail;

import com.messaging.channel.entity.ChannelConnection;
import com.messaging.channel.gmail.GmailApiClient.BatchMessageResult;
import com.messaging.channel.gmail.GmailApiClient.GmailMessage;
import com.messaging.channel.gmail.GmailSyncStateService.ClaimedBatch;
import com.messaging.channel.repository.ChannelConnectionRepository;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.NotFoundException;
import com.messaging.common.exception.TooManyRequestsException;
import com.messaging.kafka.exception.RetryableKafkaException;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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

    try {
      ChannelConnection connection =
          connectionRepository
              .findByIdAndCompanyId(connectionId, batch.companyId())
              .orElseThrow(() -> new NotFoundException("Gmail connection not found"));
      String accessToken = tokenService.accessToken(connection);

      List<BatchMessageResult> results =
          gmailApiClient.batchMessages(accessToken, batch.messageIds());
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

      boolean retry =
          commitService.commitResults(
              batch, successful, retryIds, permanentFailures, lastError);
      if (retry) {
        throw new RetryableKafkaException(
            "Gmail batch has " + retryIds.size() + " transient message failures");
      }

      finalizerService.tryFinalize(jobId);
    } catch (RetryableKafkaException exception) {
      throw exception;
    } catch (TooManyRequestsException exception) {
      boolean retry = commitService.failAttempt(batch, exception.getMessage(), true);
      if (retry) {
        throw new RetryableKafkaException("Gmail batch is rate limited", exception);
      }
      finalizerService.tryFinalize(jobId);
    } catch (BadRequestException | NotFoundException exception) {
      commitService.failAttempt(batch, exception.getMessage(), false);
      finalizerService.tryFinalize(jobId);
    } catch (RuntimeException exception) {
      boolean retry =
          commitService.failAttempt(batch, "Temporary Gmail batch processing failure", true);
      if (retry) {
        throw new RetryableKafkaException("Temporary Gmail batch processing failure", exception);
      }
      finalizerService.tryFinalize(jobId);
    }
  }
}
