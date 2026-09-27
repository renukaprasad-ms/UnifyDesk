package com.messaging.notification.platform.delivery.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.messaging.notification.platform.delivery.config.NotificationDeliveryProperties;
import com.messaging.notification.platform.delivery.metrics.NotificationDeliveryMetrics;
import com.messaging.notification.platform.delivery.model.DeliveryModels.DeliveryIntent;
import com.messaging.notification.platform.delivery.model.DeliveryModels.DeliveryMode;
import com.messaging.notification.platform.delivery.model.DeliveryModels.Channel;
import com.messaging.notification.platform.delivery.model.DeliveryModels.PushAttempt;
import com.messaging.notification.platform.delivery.model.DeliveryModels.PushAttemptState;
import com.messaging.notification.platform.delivery.model.DeliveryModels.PushSubscription;
import com.messaging.notification.platform.delivery.model.DeliveryModels.RecipientSnapshot;
import com.messaging.notification.platform.delivery.policy.DeliveryEligibilityService;
import com.messaging.notification.platform.delivery.policy.DeliveryEligibilityService.Decision;
import com.messaging.notification.platform.delivery.policy.NotificationDeliveryPolicy;
import com.messaging.notification.platform.delivery.push.PushSubscriptionService;
import com.messaging.notification.platform.delivery.push.PushSubscriptionService.DecryptedSubscription;
import com.messaging.notification.platform.delivery.push.WebPushGateway;
import com.messaging.notification.platform.delivery.push.WebPushGateway.PushSendResult;
import com.messaging.notification.platform.delivery.repository.NotificationDeliveryRuntimeRepository;
import com.messaging.notification.platform.delivery.repository.ProviderAttemptLifecycleRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class NotificationPushAttemptWorkerRetryAfterTests {

  @Test
  void normalRateLimitPersistsAndUsesSameAbsoluteProviderRetryTime() {
    NotificationDeliveryRuntimeRepository repository =
        mock(NotificationDeliveryRuntimeRepository.class);
    ProviderAttemptLifecycleRepository attempts = mock(ProviderAttemptLifecycleRepository.class);
    ProviderAttemptRecoveryService recovery = mock(ProviderAttemptRecoveryService.class);
    DeliveryEligibilityService eligibility = mock(DeliveryEligibilityService.class);
    PushSubscriptionService subscriptions = mock(PushSubscriptionService.class);
    WebPushGateway gateway = mock(WebPushGateway.class);
    NotificationDeliveryProperties properties = new NotificationDeliveryProperties();
    NotificationDeliveryMetrics metrics = mock(NotificationDeliveryMetrics.class);
    NotificationDeliveryWorker deliveryWorker = mock(NotificationDeliveryWorker.class);
    NotificationDeliveryPolicy policy = new NotificationDeliveryPolicy(properties);

    Instant now = Instant.parse("2026-09-27T08:00:00Z");
    DeliveryIntent intent =
        new DeliveryIntent(
            10L,
            11L,
            12L,
            13L,
            7L,
            "INBOX_ACTIVITY",
            null,
            Channel.WEB_PUSH,
            DeliveryMode.IMMEDIATE,
            "push:12:7",
            "DEFERRED",
            now,
            now,
            0,
            null,
            null,
            now);
    PushSubscription subscription =
        new PushSubscription(
            50L,
            7L,
            "hash",
            "endpoint-ciphertext",
            "p256dh-ciphertext",
            "auth-ciphertext",
            null,
            "Chrome",
            "ACTIVE",
            now,
            null,
            null,
            null,
            now);
    PushAttempt attempt =
        new PushAttempt(
            30L,
            10L,
            50L,
            1,
            PushAttemptState.CLAIMED,
            now,
            "push-claim",
            now.plusSeconds(120),
            null,
            null,
            null);
    RecipientSnapshot recipient =
        new RecipientSnapshot(
            7L,
            "user@example.com",
            "User",
            true,
            true,
            13L,
            "Workspace",
            "UTC",
            true,
            true);

    when(repository.findIntent(10L)).thenReturn(Optional.of(intent));
    when(repository.findSubscription(50L)).thenReturn(Optional.of(subscription));
    when(eligibility.evaluate(any(), any()))
        .thenReturn(new Decision(Decision.Action.SEND, null, null, recipient));
    when(subscriptions.decrypt(subscription))
        .thenReturn(new DecryptedSubscription("https://push.example/sub", "p256dh", "auth"));
    when(deliveryWorker.pushPayload(intent)).thenReturn("{\"v\":1}");
    when(attempts.prepareAttempt(10L, 50L, 1, "WEB_PUSH")).thenReturn(200L);
    when(attempts.markInFlight(anyLong(), any())).thenReturn(true);
    when(attempts.completeAttempt(
            anyLong(), any(), anyString(), anyInt(), anyString(), any(), anyLong()))
        .thenReturn(true);
    when(gateway.send(anyString(), anyString(), anyString(), anyString(), any(), anyString()))
        .thenReturn(
            new PushSendResult(
                PushSendResult.Outcome.RATE_LIMITED,
                429,
                Duration.ofHours(1),
                "PUSH_RATE_LIMITED"));

    NotificationPushAttemptWorker worker =
        new NotificationPushAttemptWorker(
            repository,
            attempts,
            recovery,
            eligibility,
            policy,
            subscriptions,
            gateway,
            properties,
            metrics,
            deliveryWorker);

    worker.process(attempt, now);

    ArgumentCaptor<Instant> persistedRetry = ArgumentCaptor.forClass(Instant.class);
    verify(attempts)
        .completeAttempt(
            anyLong(),
            any(),
            anyString(),
            anyInt(),
            anyString(),
            persistedRetry.capture(),
            anyLong());
    ArgumentCaptor<Instant> logicalRetry = ArgumentCaptor.forClass(Instant.class);
    verify(repository)
        .markPushAttemptRetry(
            30L,
            "push-claim",
            429,
            "PUSH_RATE_LIMITED",
            logicalRetry.capture());

    assertThat(persistedRetry.getValue()).isEqualTo(logicalRetry.getValue());
    assertThat(logicalRetry.getValue()).isAfter(Instant.now().plusSeconds(3500));
  }
}
