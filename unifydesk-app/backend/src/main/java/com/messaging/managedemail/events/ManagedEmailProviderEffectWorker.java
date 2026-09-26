package com.messaging.managedemail.events;

import com.messaging.managedemail.events.ManagedEmailProviderEffectRepository.ClaimedProviderEffect;
import com.messaging.managedemail.runtime.ManagedEmailRuntimeProperties;
import com.messaging.managedemail.runtime.ManagedEmailRuntimeRepository;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.sesv2.model.SesV2Exception;

@Slf4j
@Service
@RequiredArgsConstructor
public class ManagedEmailProviderEffectWorker {

  private final ManagedEmailProviderEffectRepository repository;
  private final ManagedEmailRuntimeRepository runtimeRepository;
  private final ManagedEmailProviderSuppressionService suppressionService;
  private final ManagedEmailRuntimeProperties properties;

  @Scheduled(
      fixedDelayString =
          "${app.managed-email.runtime.provider-effect-poll-interval-ms:1000}")
  public void poll() {
    if (!repository.available()) {
      return;
    }
    int limit = Math.max(1, properties.getProviderEffectPollBatchSize());
    for (int index = 0; index < limit; index++) {
      try {
        if (!runOnce()) {
          return;
        }
      } catch (RuntimeException exception) {
        log.warn(
            "managed_email_provider_effect_worker_interrupted reason={}",
            exception.getClass().getSimpleName());
        return;
      }
    }
  }

  public boolean runOnce() {
    Instant now = Instant.now();
    Instant staleBefore =
        now.minusSeconds(Math.max(1, properties.getProviderEffectStaleAfterSeconds()));
    ClaimedProviderEffect effect =
        repository.claimNext(now, staleBefore).orElse(null);
    if (effect == null) {
      return false;
    }

    ManagedEmailRuntimeRepository.ManagedRecipientRuntime runtime =
        runtimeRepository.findRecipientRuntime(effect.recipientId()).orElse(null);
    if (runtime == null
        || !effect.companyId().equals(runtime.companyId())
        || !effect.tenantId().equals(runtime.tenantId())
        || runtime.providerTenantName() == null
        || runtime.providerTenantName().isBlank()
        || runtime.region() == null
        || runtime.region().isBlank()) {
      repository.failed(
          effect.id(),
          effect.attemptCount(),
          Instant.now(),
          "PROVIDER_EFFECT_RUNTIME_MISMATCH");
      return true;
    }

    try {
      suppressionService.signal(runtime, effect.reason());
    } catch (RuntimeException exception) {
      providerFailure(effect, exception);
      return true;
    }

    repository.succeeded(effect.id(), effect.attemptCount(), Instant.now());
    return true;
  }

  private void providerFailure(ClaimedProviderEffect effect, RuntimeException exception) {
    int maxAttempts = Math.max(1, properties.getProviderEffectMaxAttempts());
    String code = errorCode(exception);
    if (retryable(exception) && effect.attemptCount() < maxAttempts) {
      repository.retry(
          effect.id(),
          effect.attemptCount(),
          Instant.now().plus(retryDelay(effect.attemptCount())),
          code);
      return;
    }
    repository.failed(effect.id(), effect.attemptCount(), Instant.now(), code);
  }

  private boolean retryable(RuntimeException exception) {
    if (exception instanceof SesV2Exception sesException) {
      int status = sesException.statusCode();
      return status == 429 || status >= 500;
    }
    return exception instanceof SdkClientException
        || !(exception instanceof IllegalArgumentException);
  }

  private String errorCode(RuntimeException exception) {
    if (exception instanceof SesV2Exception sesException
        && sesException.awsErrorDetails() != null
        && sesException.awsErrorDetails().errorCode() != null) {
      return sesException.awsErrorDetails().errorCode();
    }
    return exception.getClass().getSimpleName();
  }

  private Duration retryDelay(int attempt) {
    long base = Math.max(1, properties.getProviderEffectRetryInitialSeconds());
    long max = Math.max(base, properties.getProviderEffectRetryMaxSeconds());
    int exponent = Math.min(Math.max(0, attempt - 1), 16);
    long seconds = Math.min(max, base * (1L << exponent));
    return Duration.ofSeconds(seconds);
  }
}
