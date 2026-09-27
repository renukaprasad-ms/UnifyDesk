package com.messaging.notification.platform.delivery.worker;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.messaging.notification.platform.delivery.config.NotificationDeliveryProperties;
import com.messaging.notification.platform.delivery.metrics.NotificationDeliveryMetrics;
import com.messaging.notification.platform.delivery.policy.NotificationDeliveryPolicy;
import com.messaging.notification.platform.delivery.policy.ProviderAttemptRecoveryPolicy;
import com.messaging.notification.platform.delivery.repository.NotificationDeliveryRuntimeRepository;
import com.messaging.notification.platform.delivery.repository.NotificationDeliveryRuntimeRepository.PushIntentSummary;
import com.messaging.notification.platform.delivery.repository.NotificationDeliverySupportRepository;
import com.messaging.notification.platform.delivery.repository.ProviderAttemptLifecycleRepository;
import com.messaging.notification.platform.delivery.repository.ProviderAttemptLifecycleRepository.ExpiredDigestClaim;
import com.messaging.notification.platform.delivery.repository.ProviderAttemptLifecycleRepository.ExpiredEmailClaim;
import com.messaging.notification.platform.delivery.repository.ProviderAttemptLifecycleRepository.ExpiredPushClaim;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ProviderAttemptRecoveryServiceTests {

  private static final Instant NOW = Instant.parse("2026-09-27T08:00:00Z");

  private ProviderAttemptLifecycleRepository attempts;
  private NotificationDeliveryRuntimeRepository runtime;
  private NotificationDeliverySupportRepository support;
  private NotificationDeliveryMetrics metrics;
  private ProviderAttemptRecoveryService service;

  @BeforeEach
  void setUp() {
    attempts = Mockito.mock(ProviderAttemptLifecycleRepository.class);
    runtime = Mockito.mock(NotificationDeliveryRuntimeRepository.class);
    support = Mockito.mock(NotificationDeliverySupportRepository.class);
    metrics = Mockito.mock(NotificationDeliveryMetrics.class);
    NotificationDeliveryProperties properties = new NotificationDeliveryProperties();
    service =
        new ProviderAttemptRecoveryService(
            attempts,
            runtime,
            support,
            new ProviderAttemptRecoveryPolicy(),
            new NotificationDeliveryPolicy(properties),
            properties,
            metrics);
  }

  @Test
  void crashAfterEmailClaimBeforeAttemptRecordIsSafelyReclaimed() {
    when(attempts.expiredEmailClaims(anyInt()))
        .thenReturn(
            List.of(new ExpiredEmailClaim(10L, "claim", null, null, null, null, null, null)));

    service.recoverEmailClaims(NOW);

    verifyNoInteractions(runtime);
    verifyNoInteractions(metrics);
  }

  @Test
  void crashAfterEmailAttemptRecordBeforeProviderWindowIsSafelyReclaimed() {
    when(attempts.expiredEmailClaims(anyInt()))
        .thenReturn(
            List.of(
                new ExpiredEmailClaim(
                    10L, "claim", 100L, 1, "PREPARED", "UNKNOWN", null, null)));

    service.recoverEmailClaims(NOW);

    verifyNoInteractions(runtime);
    verifyNoInteractions(metrics);
  }

  @Test
  void crashInsideEmailProviderWindowBecomesUnknown() {
    when(attempts.expiredEmailClaims(anyInt()))
        .thenReturn(
            List.of(
                new ExpiredEmailClaim(
                    10L, "claim", 100L, 1, "IN_FLIGHT", "UNKNOWN", null, null)));

    service.recoverEmailClaims(NOW);

    verify(runtime)
        .markIntentTerminal(
            10L, "claim", "UNKNOWN", "AMBIGUOUS_PROVIDER_CRASH", 1);
    verify(metrics).emailUnknown();
  }

  @Test
  void crashAfterAcceptedEmailResultBeforeLogicalFinalizeReconcilesSent() {
    Instant completed = NOW.minusSeconds(5);
    when(attempts.expiredEmailClaims(anyInt()))
        .thenReturn(
            List.of(
                new ExpiredEmailClaim(
                    10L, "claim", 100L, 1, "COMPLETED", "ACCEPTED", completed, null)));

    service.recoverEmailClaims(NOW);

    verify(runtime).markIntentSent(10L, "claim", completed);
    verify(runtime, never())
        .markIntentTerminal(10L, "claim", "UNKNOWN", "AMBIGUOUS_PROVIDER_CRASH", 1);
  }

  @Test
  void digestPreparedCrashIsReclaimableAndInFlightCrashIsUnknown() {
    when(attempts.expiredDigestClaims(anyInt()))
        .thenReturn(
            List.of(
                new ExpiredDigestClaim(
                    20L, "digest-claim", 11L, 101L, 1, "PREPARED", "UNKNOWN", null, null),
                new ExpiredDigestClaim(
                    21L, "digest-claim-2", 12L, 102L, 1, "IN_FLIGHT", "UNKNOWN", null, null)));

    service.recoverDigestClaims(NOW);

    verify(support)
        .terminalDigestAndIntents(
            21L, "digest-claim-2", "UNKNOWN", "AMBIGUOUS_PROVIDER_CRASH", NOW);
    verify(support, never())
        .terminalDigestAndIntents(
            20L, "digest-claim", "UNKNOWN", "AMBIGUOUS_PROVIDER_CRASH", NOW);
  }

  @Test
  void digestCompletedAcceptedCrashReconcilesBucketSent() {
    Instant completed = NOW.minusSeconds(2);
    when(attempts.expiredDigestClaims(anyInt()))
        .thenReturn(
            List.of(
                new ExpiredDigestClaim(
                    20L,
                    "digest-claim",
                    11L,
                    101L,
                    1,
                    "COMPLETED",
                    "ACCEPTED",
                    completed,
                    null)));

    service.recoverDigestClaims(NOW);

    verify(runtime).markDigestSent(20L, "digest-claim", completed);
  }

  @Test
  void pushClaimWithoutAttemptAndPreparedAttemptAreSafelyReset() {
    when(attempts.expiredPushClaims(anyInt()))
        .thenReturn(
            List.of(
                new ExpiredPushClaim(
                    30L, 40L, 50L, "push-a", 1, null, null, null, null, null, null, null),
                new ExpiredPushClaim(
                    31L,
                    41L,
                    51L,
                    "push-b",
                    1,
                    200L,
                    "PREPARED",
                    "UNKNOWN",
                    null,
                    null,
                    null,
                    null)));

    service.recoverPushClaims(NOW);

    verify(attempts).resetPushClaimForSafeRetry(30L, "push-a");
    verify(attempts).resetPushClaimForSafeRetry(31L, "push-b");
    verify(attempts, never()).markPushIntentUnknown(40L, "AMBIGUOUS_PROVIDER_CRASH");
    verify(attempts, never()).markPushIntentUnknown(41L, "AMBIGUOUS_PROVIDER_CRASH");
  }

  @Test
  void pushInFlightCrashMarksAttemptAndIntentUnknown() {
    when(attempts.expiredPushClaims(anyInt()))
        .thenReturn(
            List.of(
                new ExpiredPushClaim(
                    30L,
                    40L,
                    50L,
                    "push",
                    1,
                    200L,
                    "IN_FLIGHT",
                    "UNKNOWN",
                    null,
                    null,
                    null,
                    null)));

    service.recoverPushClaims(NOW);

    verify(runtime)
        .markPushAttemptTerminal(
            30L, "push", "UNKNOWN", null, "AMBIGUOUS_PROVIDER_CRASH");
    verify(attempts).markPushIntentUnknown(40L, "AMBIGUOUS_PROVIDER_CRASH");
  }

  @Test
  void pushCompletedAcceptedCrashReconcilesAttemptAndIntent() {
    Instant completed = NOW.minusSeconds(1);
    when(attempts.expiredPushClaims(anyInt()))
        .thenReturn(
            List.of(
                new ExpiredPushClaim(
                    30L,
                    40L,
                    50L,
                    "push",
                    1,
                    200L,
                    "COMPLETED",
                    "ACCEPTED",
                    completed,
                    201,
                    null,
                    null)));
    when(runtime.pushIntentSummary(40L)).thenReturn(new PushIntentSummary(1, 1, 0));

    service.recoverPushClaims(NOW);

    verify(runtime).markPushAttemptAccepted(30L, "push", 201);
    verify(runtime).markSubscriptionSuccess(50L);
    verify(runtime).finalizePushIntent(40L, true, null, NOW);
  }

  @Test
  void completedRateLimitedCrashHonorsPersistedProviderRetryAfter() {
    Instant completed = NOW.minusSeconds(30);
    Instant providerRetryAfterAt = NOW.plusSeconds(3600);
    when(attempts.expiredPushClaims(anyInt()))
        .thenReturn(
            List.of(
                new ExpiredPushClaim(
                    30L,
                    40L,
                    50L,
                    "push",
                    1,
                    200L,
                    "COMPLETED",
                    "RATE_LIMITED",
                    completed,
                    429,
                    "PUSH_RATE_LIMITED",
                    providerRetryAfterAt)));

    service.recoverPushClaims(NOW);

    verify(runtime)
        .markPushAttemptRetry(
            30L,
            "push",
            429,
            "PUSH_RATE_LIMITED",
            providerRetryAfterAt);
  }

  @Test
  void completedRateLimitedCrashWithoutProviderHintUsesLocalBackoff() {
    when(attempts.expiredPushClaims(anyInt()))
        .thenReturn(
            List.of(
                new ExpiredPushClaim(
                    30L,
                    40L,
                    50L,
                    "push",
                    1,
                    200L,
                    "COMPLETED",
                    "RATE_LIMITED",
                    NOW.minusSeconds(30),
                    429,
                    "PUSH_RATE_LIMITED",
                    null)));

    service.recoverPushClaims(NOW);

    verify(runtime)
        .markPushAttemptRetry(
            30L,
            "push",
            429,
            "PUSH_RATE_LIMITED",
            NOW.plusSeconds(60));
  }
}
