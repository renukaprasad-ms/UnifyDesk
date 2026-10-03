package com.messaging.broadcast.service;

import com.messaging.billing.exception.PlanEntitlementException;
import com.messaging.broadcast.entity.*;
import com.messaging.broadcast.enums.BroadcastSenderManagementMode;
import com.messaging.broadcast.enums.BroadcastSenderProvider;
import com.messaging.broadcast.transport.*;
import com.messaging.channel.enums.ChannelType;
import com.messaging.managedemail.delivery.ManagedEmailDeliveryGuard;
import com.messaging.managedemail.delivery.ManagedEmailProviderBudgetService;
import com.messaging.managedemail.delivery.ManagedEmailRateLimiter;
import com.messaging.managedemail.delivery.ManagedEmailSuppressionGate;
import com.messaging.managedemail.events.ManagedEmailPostAcceptanceReconciler;
import com.messaging.managedemail.metering.ManagedEmailUsageService;
import com.messaging.managedemail.runtime.ManagedEmailRuntimeRepository;
import com.messaging.contacts.consent.ContactEligibilityService;
import com.messaging.contacts.consent.ConsentTypes.Channel;
import com.messaging.contacts.consent.ConsentTypes.EligibilityContext;
import com.messaging.contacts.consent.ConsentTypes.EligibilityDecision;
import com.messaging.contacts.consent.ConsentTypes.EligibilityResult;
import com.messaging.contacts.consent.ConsentTypes.Purpose;
import com.messaging.managedsms.delivery.ManagedSmsDeliveryCoordinator;
import java.time.Instant;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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
  private final BroadcastEmailComplianceService emailComplianceService;
  private final ManagedEmailDeliveryGuard managedEmailDeliveryGuard;
  private final ManagedEmailRateLimiter managedEmailRateLimiter;
  private final ManagedEmailUsageService managedEmailUsageService;
  private final ManagedEmailPostAcceptanceReconciler managedEmailPostAcceptanceReconciler;
  private final ManagedEmailSuppressionGate managedEmailSuppressionGate;
  private final ManagedEmailProviderBudgetService managedEmailProviderBudgetService;
  private final ContactEligibilityService contactEligibilityService;
  private final BroadcastWhatsAppDeliveryService whatsAppDeliveryService;

  private ManagedSmsDeliveryCoordinator managedSmsDeliveryCoordinator;

  @Autowired
  public void setManagedSmsDeliveryCoordinator(
      ManagedSmsDeliveryCoordinator managedSmsDeliveryCoordinator) {
    this.managedSmsDeliveryCoordinator = managedSmsDeliveryCoordinator;
  }

  public void deliver(
      BroadcastRun run,
      BroadcastSenderConnection connection,
      BroadcastSender sender,
      BroadcastRecipient recipient) {
    if (!recheckContactEligibility(run, recipient)) return;

    if (run.getProviderType() == BroadcastSenderProvider.META_WHATSAPP) {
      whatsAppDeliveryService.deliver(run, connection, sender, recipient);
      return;
    }

    if (connection.getManagementMode() == BroadcastSenderManagementMode.MESSAGING_MANAGED) {
      if (run.getProviderType() == BroadcastSenderProvider.MSG91_SMS) {
        if (managedSmsDeliveryCoordinator == null) {
          stateService.releaseForRateLimit(recipient.getId(), Instant.now().plusSeconds(60));
          return;
        }
        managedSmsDeliveryCoordinator.deliver(run, connection, sender, recipient);
        return;
      }
      deliverManaged(run, connection, sender, recipient);
      return;
    }
    deliverExisting(run, connection, sender, recipient);
  }

  private boolean recheckContactEligibility(
      BroadcastRun run, BroadcastRecipient recipient) {
    if (recipient.getContactId() == null) return true;

    Channel channel = Channel.valueOf(run.getProviderType().channelType().name());
    EligibilityResult result =
        contactEligibilityService.evaluate(
            run.getCompanyId(),
            recipient.getContactId(),
            recipient.getContactIdentityId(),
            channel,
            Purpose.MARKETING,
            new EligibilityContext(run.getProviderType().name(), null, null));

    if (result.decision() != EligibilityDecision.ELIGIBLE) {
      stateService.skipProcessing(
          recipient.getId(),
          "CONTACT_" + result.reason().name(),
          "Contact is no longer eligible for this broadcast");
      return false;
    }

    if (!Objects.equals(result.normalizedDestination(), recipient.getNormalizedDestination())) {
      stateService.skipProcessing(
          recipient.getId(),
          "CONTACT_IDENTITY_CHANGED",
          "Contact destination changed after audience snapshot");
      return false;
    }
    return true;
  }

  private void deliverExisting(
      BroadcastRun run,
      BroadcastSenderConnection connection,
      BroadcastSender sender,
      BroadcastRecipient recipient) {
    if (!rateLimiter.tryAcquire(connection)) {
      stateService.releaseForRateLimit(recipient.getId(), Instant.now().plusSeconds(1));
      return;
    }

    BroadcastDeliveryStateService.AttemptContext attempt =
        stateService.begin(recipient.getId(), run.getProviderType());
    try {
      BroadcastTemplateRenderer.RenderedContent rendered =
          templateRenderer.render(
              run.getSubjectSnapshot(),
              run.getBodyTextSnapshot(),
              run.getBodyHtmlSnapshot(),
              run.getTemplateVariablesJsonSnapshot(),
              recipient,
              run.getProviderType().channelType());
      if (!validateRendered(run, rendered, attempt)) return;

      if (run.getProviderType().channelType() == ChannelType.SMS) {
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

      BroadcastEmailComplianceService.CompliantContent compliant =
          emailComplianceService.apply(rendered, recipient);
      BroadcastEmailResult result =
          emailTransportRegistry
              .require(run.getProviderType())
              .send(
                  connection,
                  sender,
                  new BroadcastEmailCommand(
                      recipient.getDestination(),
                      compliant.subject(),
                      compliant.bodyText(),
                      compliant.bodyHtml(),
                      run.getProviderTemplateIdSnapshot(),
                      compliant.templateVariables(),
                      recipient.getInternetMessageId(),
                      recipient.getProviderCorrelationId(),
                      null,
                      null,
                      null,
                      null,
                      compliant.compliance()));
      stateService.accepted(attempt, result);
    } catch (BroadcastTransportException exception) {
      handleTransportException(attempt, exception);
    } catch (RuntimeException exception) {
      stateService.unknown(
          attempt,
          "UNEXPECTED_SEND_OUTCOME",
          exception.getMessage() == null
              ? exception.getClass().getSimpleName()
              : exception.getMessage());
    }
  }

  private void deliverManaged(
      BroadcastRun run,
      BroadcastSenderConnection connection,
      BroadcastSender sender,
      BroadcastRecipient recipient) {
    if (run.getProviderType() != BroadcastSenderProvider.AMAZON_SES
        || sender.getProvider() != BroadcastSenderProvider.AMAZON_SES
        || connection.getProvider() != BroadcastSenderProvider.AMAZON_SES) {
      stateService.releaseForRateLimit(recipient.getId(), Instant.now().plusSeconds(60));
      return;
    }

    ManagedEmailRuntimeRepository.ManagedSenderRuntime runtime =
        managedEmailDeliveryGuard.eligible(run, connection, sender, recipient).orElse(null);
    if (runtime == null) {
      stateService.releaseForRateLimit(recipient.getId(), Instant.now().plusSeconds(30));
      return;
    }

    BroadcastTemplateRenderer.RenderedContent rendered;
    try {
      rendered =
          templateRenderer.render(
              run.getSubjectSnapshot(),
              run.getBodyTextSnapshot(),
              run.getBodyHtmlSnapshot(),
              run.getTemplateVariablesJsonSnapshot(),
              recipient,
              ChannelType.EMAIL);
    } catch (RuntimeException exception) {
      BroadcastDeliveryStateService.AttemptContext attempt =
          stateService.begin(recipient.getId(), run.getProviderType());
      stateService.failed(
          attempt,
          "TEMPLATE_RENDER_FAILED",
          exception.getMessage() == null ? "Template rendering failed" : exception.getMessage());
      return;
    }

    if (hasValidationError(run, recipient, rendered)) return;

    if (managedEmailSuppressionGate.skipIfSuppressed(
        run.getCompanyId(), recipient.getId(), recipient.getNormalizedDestination())) {
      return;
    }

    if (!managedEmailRateLimiter.tryAcquire(runtime, run.getId())) {
      stateService.releaseForRateLimit(recipient.getId(), Instant.now().plusSeconds(1));
      return;
    }

    BroadcastDeliveryStateService.AttemptContext attempt =
        stateService.begin(recipient.getId(), run.getProviderType());
    boolean usageReserved = false;
    try {
      try {
        usageReserved =
            managedEmailProviderBudgetService.reserveForSend(
                runtime, recipient.getId(), run.getId(), attempt.attemptNo());
      } catch (PlanEntitlementException exception) {
        stateService.retry(
            attempt,
            "MANAGED_EMAIL_MONTHLY_LIMIT_REACHED",
            "Managed Email monthly limit reached",
            Instant.now().plusSeconds(60));
        return;
      }
      if (!usageReserved) {
        stateService.retry(
            attempt,
            "MANAGED_EMAIL_PROVIDER_UNAVAILABLE",
            "UnifyDesk Managed Email provider capacity is temporarily unavailable",
            Instant.now().plusSeconds(60));
        return;
      }

      BroadcastEmailComplianceService.CompliantContent compliant =
          emailComplianceService.apply(rendered, recipient);
      BroadcastEmailResult result =
          emailTransportRegistry
              .require(run.getProviderType())
              .send(
                  connection,
                  sender,
                  new BroadcastEmailCommand(
                      recipient.getDestination(),
                      compliant.subject(),
                      compliant.bodyText(),
                      compliant.bodyHtml(),
                      run.getProviderTemplateIdSnapshot(),
                      compliant.templateVariables(),
                      recipient.getInternetMessageId(),
                      recipient.getProviderCorrelationId(),
                      run.getCompanyId(),
                      run.getId(),
                      sender.getId(),
                      recipient.getId(),
                      compliant.compliance()));
      stateService.accepted(attempt, result);
      safeCommit(run, recipient, attempt, result);
      try {
        managedEmailPostAcceptanceReconciler.reconcile(recipient.getId());
      } catch (RuntimeException exception) {
        log.warn(
            "managed_email_post_acceptance_reconcile_deferred companyId={} runId={} recipientId={}",
            run.getCompanyId(),
            run.getId(),
            recipient.getId());
      }
    } catch (BroadcastTransportException exception) {
      if (usageReserved && exception.outcome() != BroadcastTransportOutcome.UNKNOWN) {
        safeRelease(run, recipient, attempt);
      }
      handleTransportException(attempt, exception);
    } catch (RuntimeException exception) {
      stateService.unknown(
          attempt,
          "UNEXPECTED_SEND_OUTCOME",
          exception.getMessage() == null
              ? exception.getClass().getSimpleName()
              : exception.getMessage());
    }
  }

  private boolean hasValidationError(
      BroadcastRun run,
      BroadcastRecipient recipient,
      BroadcastTemplateRenderer.RenderedContent rendered) {
    if (!rendered.unsafeHtml()
        && rendered.unresolvedVariables().isEmpty()
        && !(run.getProviderType() == BroadcastSenderProvider.MSG91_EMAIL
            && rendered.templateVariables().values().stream()
                .anyMatch(value -> value != null && (value.contains("<") || value.contains(">"))))) {
      return false;
    }
    BroadcastDeliveryStateService.AttemptContext attempt =
        stateService.begin(recipient.getId(), run.getProviderType());
    validateRendered(run, rendered, attempt);
    return true;
  }

  private boolean validateRendered(
      BroadcastRun run,
      BroadcastTemplateRenderer.RenderedContent rendered,
      BroadcastDeliveryStateService.AttemptContext attempt) {
    if (rendered.unsafeHtml()) {
      stateService.failed(
          attempt, "UNSAFE_TEMPLATE_VARIABLE", "Rendered HTML contains an unsafe URL scheme");
      return false;
    }
    if (run.getProviderType() == BroadcastSenderProvider.MSG91_EMAIL
        && rendered.templateVariables().values().stream()
            .anyMatch(value -> value != null && (value.contains("<") || value.contains(">")))) {
      stateService.failed(
          attempt,
          "UNSAFE_TEMPLATE_VARIABLE",
          "MSG91 template variable contains HTML markup");
      return false;
    }
    if (!rendered.unresolvedVariables().isEmpty()) {
      stateService.failed(
          attempt,
          "MISSING_TEMPLATE_VARIABLE",
          "Missing template variables: " + String.join(", ", rendered.unresolvedVariables()));
      return false;
    }
    return true;
  }

  private void handleTransportException(
      BroadcastDeliveryStateService.AttemptContext attempt,
      BroadcastTransportException exception) {
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
