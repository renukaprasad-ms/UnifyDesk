package com.messaging.channel.gmail;

import com.messaging.channel.entity.ChannelConnection;
import com.messaging.channel.enums.ChannelConnectionStatus;
import com.messaging.channel.gmail.GmailApiClient.BatchMessageResult;
import com.messaging.channel.gmail.GmailApiClient.GmailMessage;
import com.messaging.channel.gmail.GmailSyncStateService.ClaimedBatch;
import com.messaging.billing.exception.PlanEntitlementException;
import com.messaging.channel.repository.ChannelConnectionRepository;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.NotFoundException;
import com.messaging.common.exception.ServiceUnavailableException;
import com.messaging.company.enums.CompanyStatus;
import com.messaging.company.repository.CompanyRepository;
import java.time.Duration;
import java.time.Instant;
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
  private final GmailDistributedQuotaManager quotaManager;
  private final ChannelConnectionRepository connectionRepository;
  private final CompanyRepository companyRepository;

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
    String gmailAccount = null;
    ClaimedBatch attemptedBatch = batch;

    try {
      ChannelConnection connection =
          connectionRepository
              .findByIdAndCompanyId(batch.connectionId(), batch.companyId())
              .orElseThrow(() -> new NotFoundException("Gmail connection not found"));

      boolean companyActive =
          companyRepository
              .findById(batch.companyId())
              .map(company -> company.getStatus() == CompanyStatus.ACTIVE)
              .orElse(false);
      if (!companyActive || connection.getStatus() != ChannelConnectionStatus.ACTIVE) {
        commitService.failAttempt(
            batch, "Gmail sync stopped because the workspace or connection is inactive", false);
        finalizerService.tryFinalize(batch.jobId());
        return;
      }

      gmailAccount = connection.getExternalAccountId();
      if (gmailAccount == null || gmailAccount.isBlank()) {
        commitService.failAttempt(batch, "Gmail account identity is missing", false);
        finalizerService.tryFinalize(batch.jobId());
        return;
      }

      GmailQuotaReservation reservation =
          quotaManager.reserve(
              gmailAccount,
              GmailQuotaOperation.GET_MESSAGE,
              batch.messageIds().size(),
              GmailQuotaTrafficClass.BACKGROUND);
      if (!reservation.allowed()) {
        commitService.deferForQuota(batch, reservation.availableAt());
        log.debug(
            "gmail_sync_batch_deferred batchId={} jobId={} connectionId={} waitMs={} reason={}",
            batch.batchId(),
            batch.jobId(),
            batch.connectionId(),
            reservation.retryAfter().toMillis(),
            reservation.reason());
        return;
      }

      // Quota waits are not attempts. Increment only immediately before an actual Gmail call.
      attemptedBatch = stateService.startAttempt(batch.batchId());

      String accessToken = tokenService.accessToken(connection);

      long fetchStartedNanos = System.nanoTime();
      List<BatchMessageResult> results =
          gmailApiClient.batchMessages(accessToken, attemptedBatch.messageIds());
      fetchMs = elapsedMillis(fetchStartedNanos);

      List<GmailMessage> successful = new ArrayList<>(results.size());
      List<String> retryIds = new ArrayList<>();
      int permanentFailures = 0;
      String lastError = null;
      boolean providerThrottled = false;
      String providerThrottleReason = null;
      Duration providerRetryAfter = Duration.ZERO;

      for (BatchMessageResult result : results) {
        if (result.successful()) {
          successful.add(result.message());
        } else if (result.retryable()) {
          retryIds.add(result.messageId());
          lastError = result.reason();
          if (isRateLimit(result)) {
            providerThrottled = true;
            providerThrottleReason = result.reason();
            providerRetryAfter = max(providerRetryAfter, result.retryAfter());
          }
        } else {
          permanentFailures++;
          lastError = result.reason();
        }
      }

      Instant retryNotBefore = null;
      if (providerThrottled) {
        lastError =
            providerThrottleReason == null || providerThrottleReason.isBlank()
                ? "userRateLimitExceeded"
                : providerThrottleReason;
        retryNotBefore =
            quotaManager.penalize(
                gmailAccount, attemptedBatch.attempt(), providerRetryAfter);
      }

      long persistStartedNanos = System.nanoTime();
      boolean retry =
          commitService.commitResults(
              attemptedBatch,
              successful,
              retryIds,
              permanentFailures,
              lastError,
              retryNotBefore);
      persistMs = elapsedMillis(persistStartedNanos);

      log.info(
          "gmail_sync_batch_processed batchId={} jobId={} connectionId={} requested={} successful={} retry={} permanentFailures={} providerThrottled={} fetchMs={} persistMs={} totalMs={}",
          attemptedBatch.batchId(),
          attemptedBatch.jobId(),
          attemptedBatch.connectionId(),
          attemptedBatch.messageIds().size(),
          successful.size(),
          retryIds.size(),
          permanentFailures,
          providerThrottled,
          fetchMs,
          persistMs,
          elapsedMillis(startedNanos));

      if (!retry) {
        finalizerService.tryFinalize(attemptedBatch.jobId());
      }
    } catch (GmailRateLimitException exception) {
      Instant retryNotBefore =
          gmailAccount == null
              ? null
              : quotaManager.penalize(
                  gmailAccount, Math.max(1, attemptedBatch.attempt()), exception.retryAfter());
      boolean retry =
          commitService.failAttempt(
              attemptedBatch,
              exception.reason(),
              true,
              retryNotBefore);
      log.info(
          "gmail_sync_provider_backpressure batchId={} jobId={} connectionId={} attempt={} retry={} retryNotBefore={}",
          attemptedBatch.batchId(),
          attemptedBatch.jobId(),
          attemptedBatch.connectionId(),
          attemptedBatch.attempt(),
          retry,
          retryNotBefore);
      if (!retry) {
        finalizerService.tryFinalize(attemptedBatch.jobId());
      }
    } catch (ServiceUnavailableException exception) {
      boolean retry =
          commitService.failAttempt(attemptedBatch, exception.getMessage(), true);
      log.warn(
          "gmail_sync_batch_retry batchId={} jobId={} connectionId={} attempt={} fetchMs={} persistMs={} reason={}",
          attemptedBatch.batchId(),
          attemptedBatch.jobId(),
          attemptedBatch.connectionId(),
          attemptedBatch.attempt(),
          fetchMs,
          persistMs,
          exception.getClass().getSimpleName());
      if (!retry) {
        finalizerService.tryFinalize(attemptedBatch.jobId());
      }
    } catch (BadRequestException | NotFoundException | PlanEntitlementException exception) {
      commitService.failAttempt(attemptedBatch, safeMessage(exception), false);
      log.warn(
          "gmail_sync_batch_failed batchId={} jobId={} connectionId={} attempt={} reason={}",
          attemptedBatch.batchId(),
          attemptedBatch.jobId(),
          attemptedBatch.connectionId(),
          attemptedBatch.attempt(),
          safeMessage(exception));
      finalizerService.tryFinalize(attemptedBatch.jobId());
    } catch (RuntimeException exception) {
      String failure = safeMessage(exception);
      boolean retry = commitService.failAttempt(attemptedBatch, failure, true);
      log.warn(
          "gmail_sync_batch_error batchId={} jobId={} connectionId={} attempt={} retry={} totalMs={} error={} reason={}",
          attemptedBatch.batchId(),
          attemptedBatch.jobId(),
          attemptedBatch.connectionId(),
          attemptedBatch.attempt(),
          retry,
          elapsedMillis(startedNanos),
          exception.getClass().getSimpleName(),
          failure,
          exception);
      if (!retry) {
        finalizerService.tryFinalize(attemptedBatch.jobId());
      }
    }
  }

  private boolean isRateLimit(BatchMessageResult result) {
    if (result.status() == 429) {
      return true;
    }
    String reason = result.reason();
    if (reason == null) {
      return false;
    }
    return "rateLimitExceeded".equalsIgnoreCase(reason)
        || "userRateLimitExceeded".equalsIgnoreCase(reason);
  }

  private Duration max(Duration first, Duration second) {
    Duration safeFirst = first == null ? Duration.ZERO : first;
    Duration safeSecond = second == null ? Duration.ZERO : second;
    return safeSecond.compareTo(safeFirst) > 0 ? safeSecond : safeFirst;
  }

  private String safeMessage(Throwable throwable) {
    Throwable current = throwable;
    String message = null;
    while (current != null) {
      if (current.getMessage() != null && !current.getMessage().isBlank()) {
        message = current.getMessage();
      }
      current = current.getCause();
    }
    return message == null ? throwable.getClass().getSimpleName() : message;
  }

  private long elapsedMillis(long startedNanos) {
    return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);
  }
}
