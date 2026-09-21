package com.messaging.delivery.service;

import com.messaging.broadcast.config.BroadcastProperties;
import com.messaging.broadcast.dto.BroadcastStatsResponse;
import com.messaging.broadcast.entity.*;
import com.messaging.broadcast.enums.*;
import com.messaging.broadcast.repository.*;
import com.messaging.broadcast.service.*;
import com.messaging.delivery.entity.EmailDeliveryReport;
import com.messaging.delivery.repository.EmailDeliveryReportRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
public class EmailDeliveryReportReconciliationService {

  private final EmailDeliveryReportRepository reportRepository;
  private final BroadcastRecipientRepository recipientRepository;
  private final BroadcastRunRepository runRepository;
  private final BroadcastMessageRepository messageRepository;
  private final BroadcastStatsService statsService;
  private final BroadcastSuppressionService suppressionService;
  private final BroadcastProgressPublisher progressPublisher;
  private final BroadcastProperties properties;

  @Transactional
  public void reconcile(Long reportId) {
    EmailDeliveryReport report = reportRepository.findByIdForUpdate(reportId).orElse(null);
    if (report == null || report.getProcessedAt() != null) return;

    BroadcastRecipient recipient = findRecipient(report);
    if (recipient == null) {
      deferOrCompleteUnmatched(report);
      return;
    }

    BroadcastRecipient locked =
        recipientRepository.findByIdForUpdate(recipient.getId()).orElse(null);
    if (locked == null) {
      deferOrCompleteUnmatched(report);
      return;
    }

    BroadcastRecipientStatus previous = locked.getStatus();
    if (previous == BroadcastRecipientStatus.ACCEPTED_BY_PROVIDER
        || previous == BroadcastRecipientStatus.UNKNOWN) {
      locked.setStatus(BroadcastRecipientStatus.BOUNCED);
      locked.setBouncedAt(Instant.now());
      locked.setFailureCode(
          report.getStatusCode() == null ? "DELIVERY_BOUNCED" : report.getStatusCode());
      locked.setFailureReason(
          report.getDiagnosticMessage() == null
              ? "Email delivery failed after provider acceptance"
              : report.getDiagnosticMessage());
      recipientRepository.save(locked);
      statsService.transition(
          locked.getRunId(), previous, BroadcastRecipientStatus.BOUNCED);

      if (report.getBounceType() == BroadcastBounceType.HARD) {
        suppressionService.suppressHardBounce(
            locked.getCompanyId(), locked.getNormalizedDestination(), locked.getId());
      }
      markRunPartial(locked.getRunId());
    }

    report.setProcessedAt(Instant.now());
    report.setClaimedAt(null);
    report.setNextAttemptAt(null);
    report.setLastError(null);
    reportRepository.save(report);
    afterCommit(() -> progressPublisher.publish(locked.getRunId(), true));
  }

  private BroadcastRecipient findRecipient(EmailDeliveryReport report) {
    if (hasText(report.getProviderCorrelationId())) {
      BroadcastRecipient recipient =
          recipientRepository
              .findByProviderCorrelationId(report.getProviderCorrelationId().trim())
              .orElse(null);
      if (sameConnection(recipient, report)) return recipient;
    }

    if (hasText(report.getOriginalMessageId())) {
      BroadcastRecipient recipient =
          recipientRepository
              .findFirstByConnectionIdAndInternetMessageId(
                  report.getConnectionId(), report.getOriginalMessageId().trim())
              .orElse(null);
      if (recipient != null) return recipient;
    }

    if (hasText(report.getNormalizedFailedRecipient())) {
      Instant since = Instant.now().minus(properties.getDeliveryReportMatchLookback());
      List<BroadcastRecipient> candidates =
          recipientRepository
              .findTop2ByConnectionIdAndNormalizedDestinationAndStatusInAndCreatedAtAfterOrderByUpdatedAtDesc(
                  report.getConnectionId(),
                  report.getNormalizedFailedRecipient(),
                  List.of(
                      BroadcastRecipientStatus.ACCEPTED_BY_PROVIDER,
                      BroadcastRecipientStatus.UNKNOWN),
                  since);
      if (candidates.size() == 1) return candidates.getFirst();
    }
    return null;
  }

  private boolean sameConnection(
      BroadcastRecipient recipient, EmailDeliveryReport report) {
    return recipient != null && recipient.getConnectionId().equals(report.getConnectionId());
  }

  private void deferOrCompleteUnmatched(EmailDeliveryReport report) {
    report.setClaimedAt(null);
    report.setLastError("No matching broadcast recipient yet");
    if (report.getAttemptCount() >= Math.max(1, properties.getDeliveryReportMaxAttempts())) {
      report.setProcessedAt(Instant.now());
      report.setNextAttemptAt(null);
    } else {
      report.setNextAttemptAt(Instant.now().plus(retryDelay(report.getAttemptCount())));
    }
    reportRepository.save(report);
  }

  private Duration retryDelay(int attempt) {
    long initial =
        Math.max(1000L, properties.getDeliveryReportRetryInitialBackoff().toMillis());
    long max =
        Math.max(initial, properties.getDeliveryReportRetryMaxBackoff().toMillis());
    int exponent = Math.min(Math.max(0, attempt - 1), 16);
    long value;
    try {
      value = Math.multiplyExact(initial, 1L << exponent);
    } catch (ArithmeticException exception) {
      value = max;
    }
    return Duration.ofMillis(Math.min(max, value));
  }

  private void markRunPartial(Long runId) {
    BroadcastRun run = runRepository.findById(runId).orElse(null);
    if (run == null) return;
    if (run.getStatus() == BroadcastRunStatus.COMPLETED) {
      run.setStatus(BroadcastRunStatus.PARTIALLY_FAILED);
      runRepository.save(run);
      messageRepository
          .findById(run.getBroadcastMessageId())
          .ifPresent(
              message -> {
                message.setStatus(BroadcastMessageStatus.PARTIALLY_FAILED);
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

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}
