package com.messaging.notification.platform.delivery.worker;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.notification.platform.delivery.config.NotificationDeliveryProperties;
import com.messaging.notification.platform.delivery.email.NotificationEmailRenderer;
import com.messaging.notification.platform.delivery.email.ProductEmailGateway;
import com.messaging.notification.platform.delivery.email.ProductEmailGateway.EmailSendResult;
import com.messaging.notification.platform.delivery.metrics.NotificationDeliveryMetrics;
import com.messaging.notification.platform.delivery.model.DeliveryModels.Channel;
import com.messaging.notification.platform.delivery.model.DeliveryModels.DeliveryIntent;
import com.messaging.notification.platform.delivery.model.DeliveryModels.DeliveryMode;
import com.messaging.notification.platform.delivery.model.DeliveryModels.PushSubscription;
import com.messaging.notification.platform.delivery.policy.DeliveryEligibilityService;
import com.messaging.notification.platform.delivery.policy.DeliveryEligibilityService.Decision;
import com.messaging.notification.platform.delivery.policy.NotificationDeliveryPolicy;
import com.messaging.notification.platform.delivery.repository.NotificationDeliveryRuntimeRepository;
import com.messaging.notification.platform.delivery.repository.NotificationDeliverySupportRepository;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NotificationDeliveryWorker {

  private final NotificationDeliveryRuntimeRepository repository;
  private final NotificationDeliverySupportRepository supportRepository;
  private final DeliveryEligibilityService eligibilityService;
  private final NotificationDeliveryPolicy policy;
  private final ProductEmailGateway emailGateway;
  private final NotificationEmailRenderer emailRenderer;
  private final NotificationDeliveryProperties properties;
  private final NotificationDeliveryMetrics metrics;
  private final ObjectMapper objectMapper;
  private final Clock clock = Clock.systemUTC();

  @Scheduled(fixedDelayString = "${app.notification-platform.delivery.poll-delay:1s}")
  public void poll() {
    if (!properties.getDelivery().isEnabled()) {
      return;
    }
    Instant now = clock.instant();
    Instant leaseUntil = now.plus(properties.getDelivery().getClaimLease());
    List<DeliveryIntent> emails =
        repository.claimDueIntents(
            Channel.EMAIL,
            DeliveryMode.DIGEST,
            properties.getDelivery().getClaimBatchSize(),
            leaseUntil);
    emails.forEach(intent -> processEmail(intent, clock.instant()));

    List<DeliveryIntent> pushes =
        repository.claimDueIntents(
            Channel.WEB_PUSH,
            DeliveryMode.DIGEST,
            properties.getDelivery().getClaimBatchSize(),
            leaseUntil);
    pushes.forEach(intent -> preparePush(intent, clock.instant()));
  }

  void processEmail(DeliveryIntent intent, Instant now) {
    Decision decision = eligibilityService.evaluate(intent, now);
    if (decision.action() == Decision.Action.SUPPRESS) {
      repository.markIntentSuppressed(intent.id(), intent.claimToken(), decision.code(), now);
      metrics.attempt("EMAIL", "SUPPRESSED_POLICY");
      return;
    }
    if (decision.action() == Decision.Action.DEFER) {
      repository.deferIntent(intent.id(), intent.claimToken(), decision.code(), decision.deferUntil());
      return;
    }

    if (!"COMPANY_CREATED".equals(intent.notificationType())) {
      repository.markIntentSuppressed(
          intent.id(), intent.claimToken(), "UNSUPPORTED_EMAIL_TYPE", now);
      return;
    }

    int attemptNo = intent.attemptCount() + 1;
    Instant started = clock.instant();
    EmailSendResult result =
        emailGateway.send(
            decision.recipient().email(),
            emailRenderer.companyWelcome(
                decision.recipient().userName(), decision.recipient().companyName()));
    Instant completed = clock.instant();
    repository.recordDeliveryAttempt(
        intent.id(),
        null,
        attemptNo,
        "EMAIL",
        started,
        completed,
        mapEmailOutcome(result),
        null,
        result.errorCode(),
        Math.max(0, completed.toEpochMilli() - started.toEpochMilli()));
    metrics.attempt("EMAIL", mapEmailOutcome(result));

    switch (result.outcome()) {
      case ACCEPTED ->
          repository.markIntentSent(intent.id(), intent.claimToken(), completed);
      case PERMANENT_FAILURE ->
          repository.markIntentTerminal(
              intent.id(),
              intent.claimToken(),
              "FAILED_PERMANENT",
              result.errorCode(),
              attemptNo);
      case UNKNOWN -> {
        repository.markIntentTerminal(
            intent.id(), intent.claimToken(), "UNKNOWN", result.errorCode(), attemptNo);
        metrics.emailUnknown();
      }
      case TRANSIENT_FAILURE -> {
        if (policy.emailAttemptsExhausted(attemptNo)) {
          repository.markIntentTerminal(
              intent.id(), intent.claimToken(), "DEAD", result.errorCode(), attemptNo);
        } else {
          repository.markIntentRetry(
              intent.id(),
              intent.claimToken(),
              result.errorCode(),
              attemptNo,
              policy.emailRetryAt(completed, attemptNo));
        }
      }
    }
  }

  void preparePush(DeliveryIntent intent, Instant now) {
    Decision decision = eligibilityService.evaluate(intent, now);
    if (decision.action() == Decision.Action.SUPPRESS) {
      repository.markIntentSuppressed(intent.id(), intent.claimToken(), decision.code(), now);
      metrics.attempt("WEB_PUSH", "SUPPRESSED_POLICY");
      return;
    }
    if (decision.action() == Decision.Action.DEFER) {
      repository.deferIntent(intent.id(), intent.claimToken(), decision.code(), decision.deferUntil());
      return;
    }

    List<PushSubscription> subscriptions = repository.activeSubscriptions(intent.userId());
    if (subscriptions.isEmpty()) {
      repository.markIntentSuppressed(
          intent.id(), intent.claimToken(), "NO_ACTIVE_SUBSCRIPTION", now);
      return;
    }

    String payload = pushPayload(intent);
    int bytes = payload.getBytes(StandardCharsets.UTF_8).length;
    if (bytes > properties.getPush().getTargetPayloadBytes()
        || bytes >= properties.getPush().getMaxPayloadBytes()) {
      repository.markIntentTerminal(
          intent.id(), intent.claimToken(), "FAILED_PERMANENT", "PUSH_PAYLOAD_TOO_LARGE", 0);
      return;
    }

    repository.ensurePushAttempts(intent.id(), subscriptions);
    supportRepository.releasePushIntent(intent.id(), intent.claimToken(), now.plusSeconds(4 * 60 * 60));
  }

  String pushPayload(DeliveryIntent intent) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("version", 1);
    payload.put("notificationId", intent.notificationId() == null ? null : Long.toString(intent.notificationId()));
    payload.put("companyId", intent.companyId() == null ? null : Long.toString(intent.companyId()));
    payload.put("type", intent.notificationType());
    payload.put("title", "New UnifyDesk activity");
    payload.put("body", "You have new unread activity in UnifyDesk.");
    String groupKey = supportRepository.notificationGroupKey(intent.notificationId());
    String tag =
        groupKey == null || groupKey.isBlank()
            ? "ud:notification:" + (intent.companyId() == null ? "account" : intent.companyId())
            : "ud:" + groupKey.replace(':', '-');
    payload.put("tag", tag);
    String openPath =
        "/notification-open?n="
            + (intent.notificationId() == null ? "" : intent.notificationId())
            + (intent.companyId() == null ? "" : "&c=" + intent.companyId());
    payload.put("openPath", openPath);
    try {
      return objectMapper.writeValueAsString(payload);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Unable to render privacy-safe push payload", exception);
    }
  }

  private String mapEmailOutcome(EmailSendResult result) {
    return switch (result.outcome()) {
      case ACCEPTED -> "ACCEPTED";
      case TRANSIENT_FAILURE -> "TRANSIENT_FAILURE";
      case PERMANENT_FAILURE -> "PERMANENT_FAILURE";
      case UNKNOWN -> "UNKNOWN";
    };
  }
}
