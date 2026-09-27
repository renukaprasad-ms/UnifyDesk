package com.messaging.managedsms.delivery;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.billing.exception.PlanEntitlementException;
import com.messaging.broadcast.entity.BroadcastRecipient;
import com.messaging.broadcast.entity.BroadcastRun;
import com.messaging.broadcast.entity.BroadcastSender;
import com.messaging.broadcast.entity.BroadcastSenderConnection;
import com.messaging.broadcast.service.BroadcastDeliveryStateService;
import com.messaging.broadcast.service.BroadcastRetryPolicy;
import com.messaging.broadcast.transport.BroadcastTransportException;
import com.messaging.broadcast.transport.BroadcastTransportOutcome;
import com.messaging.common.exception.BadRequestException;
import com.messaging.managedsms.metering.ManagedSmsSegmentCalculator;
import com.messaging.managedsms.metering.ManagedSmsUsageService;
import com.messaging.managedsms.runtime.ManagedSmsRuntimeRepository;
import com.messaging.managedsms.runtime.ManagedSmsTemplateService;
import com.messaging.managedsms.safety.ManagedSmsProviderSafetyService;
import com.messaging.managedsms.safety.ManagedSmsRateLimiter;
import java.time.Instant;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ManagedSmsDeliveryCoordinator {

  private final ManagedSmsDeliveryGuard deliveryGuard;
  private final ManagedSmsRateLimiter rateLimiter;
  private final ManagedSmsUsageService usageService;
  private final ManagedSmsSegmentCalculator segmentCalculator;
  private final ManagedSmsTemplateService templateService;
  private final ManagedSmsCorrelationService correlationService;
  private final ManagedMsg91Gateway gateway;
  private final ManagedSmsProviderSafetyService providerSafetyService;
  private final BroadcastDeliveryStateService stateService;
  private final BroadcastRetryPolicy retryPolicy;
  private final ObjectMapper objectMapper;

  public void deliver(
      BroadcastRun run,
      BroadcastSenderConnection connection,
      BroadcastSender sender,
      BroadcastRecipient recipient) {
    ManagedSmsRuntimeRepository.DeliveryRuntime runtime =
        deliveryGuard.eligible(run, connection, sender, recipient).orElse(null);
    if (runtime == null) {
      stateService.releaseForRateLimit(recipient.getId(), Instant.now().plusSeconds(30));
      return;
    }
    if (!rateLimiter.tryAcquire(runtime, run.getId())) {
      stateService.releaseForRateLimit(recipient.getId(), Instant.now().plusSeconds(1));
      return;
    }

    BroadcastDeliveryStateService.AttemptContext attempt =
        stateService.begin(recipient.getId(), run.getProviderType());
    boolean reserved = false;
    boolean providerSubmissionStarted = false;
    try {
      Map<String, String> variables = readVariables(run.getTemplateVariablesJsonSnapshot());
      ManagedSmsTemplateService.RenderedTemplate rendered =
          templateService.render(
              runtime.approvedBodySnapshot(),
              runtime.bodyFingerprintSnapshot(),
              runtime.variablesSchemaSnapshot(),
              runtime.ctaPolicySnapshot(),
              variables);
      if (!rendered.body().equals(run.getBodyTextSnapshot())) {
        stateService.failed(
            attempt, "MANAGED_SMS_TEMPLATE_NOT_READY", "Managed SMS template snapshot mismatch");
        return;
      }

      int segments = segmentCalculator.segments(rendered.body());
      usageService.reserve(
          run.getCompanyId(), run.getId(), recipient.getId(), attempt.attemptNo(), segments);
      reserved = true;

      String correlation =
          correlationService.create(
              run.getCompanyId(), run.getId(), recipient.getId(), attempt.attemptNo());

      providerSubmissionStarted = true;
      ManagedSmsSendResult result =
          gateway.send(
              runtime,
              recipient.getDestination(),
              rendered.variables(),
              rendered.variableSpecs(),
              correlation);
      correlationService.accepted(correlation, result.providerRequestId());
      stateService.accepted(attempt, result.providerRequestId(), null, result.acceptedAt());

      try {
        usageService.commit(
            run.getCompanyId(),
            run.getId(),
            recipient.getId(),
            attempt.attemptNo(),
            result.providerRequestId(),
            runtime.messageCategory(),
            null);
      } catch (RuntimeException exception) {
        log.warn(
            "managed_sms_usage_commit_deferred companyId={} runId={} recipientId={}",
            run.getCompanyId(),
            run.getId(),
            recipient.getId());
      }
    } catch (PlanEntitlementException exception) {
      stateService.retry(
          attempt,
          "MANAGED_SMS_LIMIT_REACHED",
          "Managed SMS usage limit reached",
          Instant.now().plusSeconds(60));
    } catch (BadRequestException exception) {
      if (reserved) {
        usageService.release(run.getCompanyId(), recipient.getId(), attempt.attemptNo());
      }
      stateService.failed(attempt, "MANAGED_SMS_VARIABLE_INVALID", exception.getMessage());
    } catch (BroadcastTransportException exception) {
      if (reserved) {
        if (exception.outcome() == BroadcastTransportOutcome.UNKNOWN) {
          usageService.holdUnknown(run.getCompanyId(), recipient.getId(), attempt.attemptNo());
        } else {
          usageService.release(run.getCompanyId(), recipient.getId(), attempt.attemptNo());
        }
      }
      if ("MANAGED_SMS_PROVIDER_CREDIT".equals(exception.code())) {
        providerSafetyService.tripInsufficientCredit(runtime.providerAccountKey());
      }
      handleTransportException(attempt, exception);
    } catch (RuntimeException exception) {
      if (reserved) {
        if (providerSubmissionStarted) {
          usageService.holdUnknown(run.getCompanyId(), recipient.getId(), attempt.attemptNo());
        } else {
          usageService.release(run.getCompanyId(), recipient.getId(), attempt.attemptNo());
        }
      }
      if (providerSubmissionStarted) {
        stateService.unknown(
            attempt, "MANAGED_SMS_UNKNOWN", "Managed SMS send outcome is unknown");
      } else if (retryPolicy.canRetry(attempt.attemptNo())) {
        stateService.retry(
            attempt,
            "MANAGED_SMS_PRE_SEND_FAILURE",
            "Managed SMS send could not start",
            retryPolicy.nextAttempt(attempt.attemptNo()));
      } else {
        stateService.failed(
            attempt, "MANAGED_SMS_PRE_SEND_FAILURE", "Managed SMS send could not start");
      }
    }
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

  private Map<String, String> readVariables(String json) {
    if (json == null || json.isBlank()) return Map.of();
    try {
      Map<String, String> values =
          objectMapper.readValue(json, new TypeReference<Map<String, String>>() {});
      return values == null ? Map.of() : Map.copyOf(values);
    } catch (Exception exception) {
      throw new BadRequestException("MANAGED_SMS_VARIABLE_INVALID");
    }
  }
}
