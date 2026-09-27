package com.messaging.notification.platform.delivery.worker;

import com.messaging.notification.platform.delivery.config.NotificationDeliveryProperties;
import com.messaging.notification.platform.delivery.metrics.NotificationDeliveryMetrics;
import com.messaging.notification.platform.delivery.model.DeliveryModels.DeliveryIntent;
import com.messaging.notification.platform.delivery.model.DeliveryModels.PushAttempt;
import com.messaging.notification.platform.delivery.model.DeliveryModels.PushSubscription;
import com.messaging.notification.platform.delivery.policy.DeliveryEligibilityService;
import com.messaging.notification.platform.delivery.policy.DeliveryEligibilityService.Decision;
import com.messaging.notification.platform.delivery.policy.NotificationDeliveryPolicy;
import com.messaging.notification.platform.delivery.push.PushSubscriptionService;
import com.messaging.notification.platform.delivery.push.PushSubscriptionService.DecryptedSubscription;
import com.messaging.notification.platform.delivery.push.WebPushGateway;
import com.messaging.notification.platform.delivery.push.WebPushGateway.PushSendResult;
import com.messaging.notification.platform.delivery.repository.NotificationDeliveryRuntimeRepository;
import com.messaging.notification.platform.delivery.repository.NotificationDeliveryRuntimeRepository.PushIntentSummary;
import com.messaging.notification.platform.delivery.repository.ProviderAttemptLifecycleRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NotificationPushAttemptWorker {

  private final NotificationDeliveryRuntimeRepository repository;
  private final ProviderAttemptLifecycleRepository attemptLifecycleRepository;
  private final ProviderAttemptRecoveryService recoveryService;
  private final DeliveryEligibilityService eligibilityService;
  private final NotificationDeliveryPolicy policy;
  private final PushSubscriptionService subscriptionService;
  private final WebPushGateway pushGateway;
  private final NotificationDeliveryProperties properties;
  private final NotificationDeliveryMetrics metrics;
  private final NotificationDeliveryWorker deliveryWorker;
  private final Clock clock = Clock.systemUTC();

  @Scheduled(fixedDelayString = "${app.notification-platform.delivery.poll-delay:1s}")
  public void poll() {
    Instant now = clock.instant();
    recoveryService.recoverPushClaims(now);
    List<PushAttempt> attempts =
        repository.claimDuePushAttempts(
            properties.getDelivery().getClaimBatchSize(),
            now.plus(properties.getDelivery().getClaimLease()));
    attempts.forEach(attempt -> process(attempt, clock.instant()));
  }

  @Scheduled(fixedDelayString = "${app.notification-platform.push.cleanup-delay:1h}")
  public void expireSubscriptions() {
    repository.expireSubscriptions(properties.getDelivery().getClaimBatchSize());
  }

  void process(PushAttempt attempt, Instant now) {
    DeliveryIntent intent = repository.findIntent(attempt.intentId()).orElse(null);
    PushSubscription subscription = repository.findSubscription(attempt.subscriptionId()).orElse(null);
    if (intent == null || subscription == null) {
      terminal(attempt, null, "PUSH_LOCAL_STATE_MISSING", now);
      return;
    }
    if (subscription.userId() != intent.userId() || !"ACTIVE".equals(subscription.status())) {
      terminal(attempt, null, "PUSH_SUBSCRIPTION_INACTIVE", now);
      return;
    }

    Decision decision = eligibilityService.evaluate(intent, now);
    if (decision.action() == Decision.Action.SUPPRESS) {
      terminal(attempt, null, decision.code(), now);
      return;
    }
    if (decision.action() == Decision.Action.DEFER) {
      repository.markPushAttemptRetry(
          attempt.id(), attempt.claimToken(), null, decision.code(), decision.deferUntil());
      return;
    }

    DecryptedSubscription decrypted;
    try {
      decrypted = subscriptionService.decrypt(subscription);
    } catch (RuntimeException exception) {
      repository.markSubscriptionInvalid(subscription.id(), "INVALID");
      terminal(attempt, null, "PUSH_SECRET_DECRYPT_FAILED", now);
      return;
    }

    String payload = deliveryWorker.pushPayload(intent);
    Duration ttl =
        "INBOX_ACTIVITY".equals(intent.notificationType())
            ? properties.getPush().getInboxTtl()
            : properties.getPush().getDefaultTtl();
    Instant started = clock.instant();
    long deliveryAttemptId =
        attemptLifecycleRepository.prepareAttempt(
            intent.id(), subscription.id(), attempt.attemptNo(), "WEB_PUSH");
    if (!attemptLifecycleRepository.markInFlight(deliveryAttemptId, clock.instant())) {
      return;
    }
    PushSendResult result =
        pushGateway.send(
            decrypted.endpoint(), decrypted.p256dh(), decrypted.auth(), payload, ttl, "normal");
    Instant completed = clock.instant();
    Instant providerRetryAfterAt =
        result.retryAfter() == null ? null : completed.plus(result.retryAfter());
    String outcome = outcome(result);
    if (!attemptLifecycleRepository.completeAttempt(
        deliveryAttemptId,
        completed,
        outcome,
        result.statusCode(),
        result.errorCode(),
        providerRetryAfterAt,
        completed.toEpochMilli() - started.toEpochMilli())) {
      return;
    }
    metrics.pushAttempt(outcome, result.statusCode());

    switch (result.outcome()) {
      case ACCEPTED -> {
        repository.markPushAttemptAccepted(attempt.id(), attempt.claimToken(), result.statusCode());
        repository.markSubscriptionSuccess(subscription.id());
      }
      case EXPIRED -> {
        repository.markPushAttemptTerminal(
            attempt.id(),
            attempt.claimToken(),
            "FAILED_PERMANENT",
            result.statusCode(),
            result.errorCode());
        repository.markSubscriptionInvalid(subscription.id(), "EXPIRED");
      }
      case PERMANENT_FAILURE ->
          repository.markPushAttemptTerminal(
              attempt.id(),
              attempt.claimToken(),
              "FAILED_PERMANENT",
              result.statusCode(),
              result.errorCode());
      case RATE_LIMITED, TRANSIENT_FAILURE -> {
        if (policy.pushAttemptsExhausted(attempt.attemptNo())) {
          repository.markPushAttemptTerminal(
              attempt.id(),
              attempt.claimToken(),
              "DEAD",
              result.statusCode(),
              result.errorCode());
        } else {
          Instant next =
              providerRetryAfterAt != null
                  ? providerRetryAfterAt
                  : policy.pushRetryAt(completed, attempt.attemptNo());
          repository.markPushAttemptRetry(
              attempt.id(), attempt.claimToken(), result.statusCode(), result.errorCode(), next);
          return;
        }
      }
    }
    finalizeIntentIfReady(intent.id(), completed);
  }

  private void terminal(PushAttempt attempt, Integer status, String code, Instant now) {
    repository.markPushAttemptTerminal(
        attempt.id(), attempt.claimToken(), "FAILED_PERMANENT", status, code);
    finalizeIntentIfReady(attempt.intentId(), now);
  }

  private void finalizeIntentIfReady(long intentId, Instant now) {
    PushIntentSummary summary = repository.pushIntentSummary(intentId);
    if (summary.total() > 0 && summary.pending() == 0) {
      repository.finalizePushIntent(
          intentId, summary.accepted() > 0, summary.accepted() > 0 ? null : "PUSH_ALL_FAILED", now);
    }
  }

  private String outcome(PushSendResult result) {
    return switch (result.outcome()) {
      case ACCEPTED -> "ACCEPTED";
      case RATE_LIMITED -> "RATE_LIMITED";
      case TRANSIENT_FAILURE -> "TRANSIENT_FAILURE";
      case EXPIRED, PERMANENT_FAILURE -> "PERMANENT_FAILURE";
    };
  }
}
