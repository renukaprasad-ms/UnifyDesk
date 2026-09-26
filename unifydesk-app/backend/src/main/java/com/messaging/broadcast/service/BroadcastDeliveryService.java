package com.messaging.broadcast.service;

import com.messaging.billing.exception.PlanEntitlementException;
import com.messaging.broadcast.entity.*;
import com.messaging.broadcast.enums.BroadcastSenderManagementMode;
import com.messaging.broadcast.transport.*;
import com.messaging.channel.enums.ChannelType;
import com.messaging.managedemail.delivery.ManagedEmailDeliveryGuard;
import com.messaging.managedemail.delivery.ManagedEmailRateLimiter;
import com.messaging.managedemail.metering.ManagedEmailUsageService;
import com.messaging.managedemail.runtime.ManagedEmailRuntimeRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class BroadcastDeliveryService {

  private final BroadcastEmailTransportRegistry emailTransportRegistry;
  private final BroadcastSmsTransportRegistry smsTransportRegistry;
  private final BroadcastRateLimiter rateLimiter;
  private final BroadcastRetryPolicy retryPolicy;
  private final BroadcastDeliveryStateService stateService;
  private final BroadcastTemplateRenderer templateRenderer;
  private final ManagedEmailDeliveryGuard managedEmailDeliveryGuard;
  private final ManagedEmailRateLimiter managedEmailRateLimiter;
  private final ManagedEmailUsageService managedEmailUsageService;

  public void deliver(
      BroadcastRun run,
      BroadcastSenderConnection connection,
      BroadcastSender sender,
      BroadcastRecipient recipient) {
    boolean managed =
        connection.getManagementMode() == BroadcastSenderManagementMode.MESSAGING_MANAGED;
    ManagedEmailRuntimeRepository.ManagedSenderRuntime managedRuntime = null;

    if (managed) {
      managedRuntime =
          managedEmailDeliveryGuard.eligible(run, connection, sender, recipient).orElse(null);
      if (managedRuntime == null) {
        stateService.releaseForRateLimit(recipient.getId(), Instant.now().plusSeconds(30));
        return;
      }
    } else if (!rateLimiter.tryAcquire(connection)) {
      stateService.releaseForRateLimit(recipient.getId(), Instant.now().plusSeconds(1));
      return;
    }

    ChannelType channelType = run.getProviderType().channelType();
    BroadcastTemplateRenderer.RenderedContent rendered;
    try {
      rendered =
          templateRenderer.render(
              run.getSubjectSnapshot(),
              run.getBodyTextSnapshot(),
              run.getBodyHtmlSnapshot(),
              run.getTemplateVariablesJsonSnapshot(),
              recipient,
              channelType);
    } catch (RuntimeException exception) {
      BroadcastDeliveryStateService.AttemptContext attempt =
          stateService.begin(recipient.getId(), run.getProviderType());
      stateService.failed(
          attempt,
          "TEMPLATE_RENDER_FAILED",
          exception.getMessage() == null ? "Template rendering failed" : exception.getMessage());
      return;
    }

    BroadcastDeliveryStateService.AttemptContext validationAttempt =
        validateRendered(run, recipient, rendered);
    if (validationAttempt != null) return;

    if (managed && !managedEmailRateLimiter.tryAcquire(managedRuntime, run.getId())) {
      stateService.releaseForRateLimit(recipient.getId(), Instant.now().plusSeconds(1));
      return;
    }

    BroadcastDeliveryStateService.AttemptContext attempt =
        stateService.begin(recipient.getId(), run.getProviderType());
    boolean usageReserved = false;

    try {
      if (channelType == ChannelType.SMS) {
        BroadcastSmsResult result =
            smsTransportRegistry
                .require(run.getProviderType())
                .send(
                    connection,
                    sender,
                    new BroadcastSmsCommand(
                        recipient.getDestination(),
                        rendered.bodyText(),
                        recipient.getProviderCorrelationId()));
        stateService.accepted(attempt, result.providerMessageId(), null, result.acceptedAt());
        return;
      }

      if (managed) {
        try {
          managedEmailUsageService.reserve(
              run.getCompanyId(), recipient.getId(), run.getId(), attempt.attemptNo());
          usageReserved = true;
        } catch (PlanEntitlementException exception) {
          stateService.retry(
              attempt,
              "MANAGED_EMAIL_MONTHLY_LIMIT_REACHED",
              "Managed Email monthly limit reached",
              Instant.now().plusSeconds(60));
          return;
        }
      }

      BroadcastEmailResult result =
          emailTransportRegistry
              .require(run.getProviderType())
              .send(
                  connection,
                  sender,
                  new BroadcastEmailCommand(
                      recipient.getDestination(),
                      rendered.subject(),
                      rendered.bodyText(),
                      rendered.bodyHtml(),
                      run.getProviderTemplateIdSnapshot(),
                      rendered.templateVariables(),
                      recipient.getInternetMessageId(),
                      recipient.getProviderCorrelationId(),
                      run.getCompanyId(),
                      run.getId(),
                      sender.getId(),
                      recipient.getId()));
      stateService.accepted(attempt, result);

      if (managed && usageReserved) {
        safeCommit(run, recipient, attempt, result);
      }
    } catch (BroadcastTransportException exception) {
      if (managed
          && usageReserved
          && exception.outcome() != BroadcastTransportOutcome.UNKNOWN) {
        safeRelease(run, recipient, attempt);
      }
      switch (exception.outcome()) {
        case RETRYABLE -> {
          if (retryPolicy.canRetry(attempt.attemptNo())) {
            stateService.retry(
                attempt,
                exception.code(),
                exception.getMessage(),
                retryPolicy.nextAttempt(attempt.attemptNo()));
          } else {
            stateService.failed(attempt, "RETRY_EXHAUSTED", exception.getMessage());
          }
        }
        case PERMANENT -> stateService.failed(attempt, exception.code(), exception.getMessage());
        case UNKNOWN -> stateService.unknown(attempt, exception.code(), exception.getMessage());
      }
    } catch (RuntimeException exception) {
      stateService.unknown(
          attempt,
          "UNEXPECTED_SEND_OUTCOME",
          exception.getMessage() == null
              ? exception.getClass().getSimpleName()
              : exception.getMessage());
    }
  }

  private BroadcastDeliveryStateService.AttemptContext validateRendered(
      BroadcastRun run,
      BroadcastRecipient recipient,
      BroadcastTemplateRenderer.RenderedContent rendered) {
    if (rendered.unsafeHtml()) {
      BroadcastDeliveryStateService.AttemptContext attempt =
          stateService.begin(recipient.getId(), run.getProviderType());
      stateService.failed(
          attempt, "UNSAFE_TEMPLATE_VARIABLE", "Rendered HTML contains an unsafe URL scheme");
      return attempt;
    }
    if (run.getProviderType() == com.messaging.broadcast.enums.BroadcastSenderProvider.MSG91_EMAIL
        && rendered.templateVariables().values().stream()
            .anyMatch(value -> value != null && (value.contains("<") || value.contains(">")))) {
      BroadcastDeliveryStateService.AttemptContext attempt =
          stateService.begin(recipient.getId(), run.getProviderType());
      stateService.failed(
          attempt,
          "UNSAFE_TEMPLATE_VARIABLE",
          "MSG91 template variable contains HTML markup");
      return attempt;
    }
    if (!rendered.unresolvedVariables().isEmpty()) {
      BroadcastDeliveryStateService.AttemptContext attempt =
          stateService.begin(recipient.getId(), run.getProviderType());
      stateService.failed(
          attempt,
          "MISSING_TEMPLATE_VARIABLE",
          "Missing template variables: " + String.join(", ", rendered.unresolvedVariables()));
      return attempt;
    }
    return null;
  }

  private void safeCommit(
      BroadcastRun run,
      BroadcastRecipient recipient,
      BroadcastDeliveryStateService.AttemptContext attempt,
      BroadcastEmailResult result) {
    try {
      managedEmailUsageService.commit(
          run.getCompanyId(),
          recipient.getId(),
          run.getId(),
          attempt.attemptNo(),
          result.providerMessageId());
    } catch (RuntimeException exception) {
      log.warn(
          "managed_email_usage_commit_deferred companyId={} runId={} recipientId={}",
          run.getCompanyId(),
          run.getId(),
          recipient.getId());
    }
  }

  private void safeRelease(
      BroadcastRun run,
      BroadcastRecipient recipient,
      BroadcastDeliveryStateService.AttemptContext attempt) {
    try {
      managedEmailUsageService.release(
          run.getCompanyId(), recipient.getId(), run.getId(), attempt.attemptNo());
    } catch (RuntimeException exception) {
      log.warn(
          "managed_email_usage_release_deferred companyId={} runId={} recipientId={}",
          run.getCompanyId(),
          run.getId(),
          recipient.getId());
    }
  }
}
