package com.messaging.notification.platform.delivery.worker;

import com.messaging.notification.platform.delivery.config.NotificationDeliveryProperties;
import com.messaging.notification.platform.delivery.metrics.NotificationDeliveryMetrics;
import com.messaging.notification.platform.delivery.policy.NotificationDeliveryPolicy;
import com.messaging.notification.platform.delivery.policy.ProviderAttemptRecoveryPolicy;
import com.messaging.notification.platform.delivery.policy.ProviderAttemptRecoveryPolicy.RecoveryAction;
import com.messaging.notification.platform.delivery.repository.NotificationDeliveryRuntimeRepository;
import com.messaging.notification.platform.delivery.repository.NotificationDeliveryRuntimeRepository.PushIntentSummary;
import com.messaging.notification.platform.delivery.repository.NotificationDeliverySupportRepository;
import com.messaging.notification.platform.delivery.repository.ProviderAttemptLifecycleRepository;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProviderAttemptRecoveryService {

  private static final String AMBIGUOUS_PROVIDER_CRASH = "AMBIGUOUS_PROVIDER_CRASH";

  private final ProviderAttemptLifecycleRepository attemptRepository;
  private final NotificationDeliveryRuntimeRepository runtimeRepository;
  private final NotificationDeliverySupportRepository supportRepository;
  private final ProviderAttemptRecoveryPolicy recoveryPolicy;
  private final NotificationDeliveryPolicy deliveryPolicy;
  private final NotificationDeliveryProperties properties;
  private final NotificationDeliveryMetrics metrics;

  public void recoverEmailClaims(Instant now) {
    int limit = properties.getDelivery().getClaimBatchSize();
    for (var claim : attemptRepository.expiredEmailClaims(limit)) {
      if (claim.attemptId() == null) {
        continue;
      }
      RecoveryAction action =
          recoveryPolicy.decide(
              claim.providerCallState(), claim.outcome(), claim.completedAt() != null);
      int attemptNo = claim.attemptNo() == null ? 0 : claim.attemptNo();
      switch (action) {
        case RECLAIM -> {
          // Leave the expired CLAIMED row in place. Normal claim SQL safely reclaims it.
        }
        case ACCEPTED ->
            runtimeRepository.markIntentSent(
                claim.intentId(), claim.claimToken(), completedOrNow(claim.completedAt(), now));
        case RETRY ->
            runtimeRepository.markIntentRetry(
                claim.intentId(),
                claim.claimToken(),
                safeCode(claim.errorCode(), "PROVIDER_RETRY_RECOVERY"),
                attemptNo,
                deliveryPolicy.emailRetryAt(now, Math.max(1, attemptNo)));
        case PERMANENT_FAILURE ->
            runtimeRepository.markIntentTerminal(
                claim.intentId(),
                claim.claimToken(),
                "FAILED_PERMANENT",
                safeCode(claim.errorCode(), "PROVIDER_PERMANENT_RECOVERY"),
                attemptNo);
        case SUPPRESSED ->
            runtimeRepository.markIntentSuppressed(
                claim.intentId(),
                claim.claimToken(),
                safeCode(claim.errorCode(), "PROVIDER_SUPPRESSED_RECOVERY"),
                completedOrNow(claim.completedAt(), now));
        case UNKNOWN -> {
          runtimeRepository.markIntentTerminal(
              claim.intentId(),
              claim.claimToken(),
              "UNKNOWN",
              AMBIGUOUS_PROVIDER_CRASH,
              attemptNo);
          metrics.emailUnknown();
        }
      }
    }
  }

  public void recoverDigestClaims(Instant now) {
    int limit = properties.getDelivery().getClaimBatchSize();
    for (var claim : attemptRepository.expiredDigestClaims(limit)) {
      if (claim.attemptId() == null) {
        continue;
      }
      RecoveryAction action =
          recoveryPolicy.decide(
              claim.providerCallState(), claim.outcome(), claim.completedAt() != null);
      int attemptNo = claim.attemptNo() == null ? 0 : claim.attemptNo();
      switch (action) {
        case RECLAIM -> {
          // Normal digest claim SQL will reclaim the expired bucket.
        }
        case ACCEPTED ->
            runtimeRepository.markDigestSent(
                claim.bucketId(), claim.claimToken(), completedOrNow(claim.completedAt(), now));
        case RETRY ->
            runtimeRepository.retryDigest(
                claim.bucketId(),
                claim.claimToken(),
                safeCode(claim.errorCode(), "PROVIDER_RETRY_RECOVERY"),
                deliveryPolicy.emailRetryAt(now, Math.max(1, attemptNo)));
        case PERMANENT_FAILURE ->
            supportRepository.terminalDigestAndIntents(
                claim.bucketId(),
                claim.claimToken(),
                "DEAD",
                safeCode(claim.errorCode(), "PROVIDER_PERMANENT_RECOVERY"),
                now);
        case SUPPRESSED ->
            runtimeRepository.markDigestSuppressed(
                claim.bucketId(),
                claim.claimToken(),
                safeCode(claim.errorCode(), "PROVIDER_SUPPRESSED_RECOVERY"),
                now);
        case UNKNOWN -> {
          supportRepository.terminalDigestAndIntents(
              claim.bucketId(), claim.claimToken(), "UNKNOWN", AMBIGUOUS_PROVIDER_CRASH, now);
          metrics.emailUnknown();
        }
      }
    }
  }

  public void recoverPushClaims(Instant now) {
    int limit = properties.getDelivery().getClaimBatchSize();
    Set<Long> finalizationCandidates = new LinkedHashSet<>();
    for (var claim : attemptRepository.expiredPushClaims(limit)) {
      if (claim.deliveryAttemptId() == null) {
        attemptRepository.resetPushClaimForSafeRetry(claim.pushAttemptId(), claim.claimToken());
        continue;
      }
      RecoveryAction action =
          recoveryPolicy.decide(
              claim.providerCallState(), claim.outcome(), claim.completedAt() != null);
      switch (action) {
        case RECLAIM ->
            attemptRepository.resetPushClaimForSafeRetry(
                claim.pushAttemptId(), claim.claimToken());
        case ACCEPTED -> {
          runtimeRepository.markPushAttemptAccepted(
              claim.pushAttemptId(),
              claim.claimToken(),
              claim.providerStatusCode() == null ? 200 : claim.providerStatusCode());
          runtimeRepository.markSubscriptionSuccess(claim.subscriptionId());
          finalizationCandidates.add(claim.intentId());
        }
        case RETRY ->
            runtimeRepository.markPushAttemptRetry(
                claim.pushAttemptId(),
                claim.claimToken(),
                claim.providerStatusCode(),
                safeCode(claim.errorCode(), "PUSH_RETRY_RECOVERY"),
                deliveryPolicy.pushRetryAt(now, Math.max(1, claim.attemptNo())));
        case PERMANENT_FAILURE, SUPPRESSED -> {
          runtimeRepository.markPushAttemptTerminal(
              claim.pushAttemptId(),
              claim.claimToken(),
              "FAILED_PERMANENT",
              claim.providerStatusCode(),
              safeCode(claim.errorCode(), "PUSH_PERMANENT_RECOVERY"));
          if ("PUSH_SUBSCRIPTION_EXPIRED".equals(claim.errorCode())) {
            runtimeRepository.markSubscriptionInvalid(claim.subscriptionId(), "EXPIRED");
          }
          finalizationCandidates.add(claim.intentId());
        }
        case UNKNOWN -> {
          runtimeRepository.markPushAttemptTerminal(
              claim.pushAttemptId(),
              claim.claimToken(),
              "UNKNOWN",
              claim.providerStatusCode(),
              AMBIGUOUS_PROVIDER_CRASH);
          attemptRepository.markPushIntentUnknown(claim.intentId(), AMBIGUOUS_PROVIDER_CRASH);
        }
      }
    }
    finalizationCandidates.forEach(intentId -> finalizePushIntentIfReady(intentId, now));
  }

  private void finalizePushIntentIfReady(long intentId, Instant now) {
    PushIntentSummary summary = runtimeRepository.pushIntentSummary(intentId);
    if (summary.total() > 0 && summary.pending() == 0) {
      runtimeRepository.finalizePushIntent(
          intentId,
          summary.accepted() > 0,
          summary.accepted() > 0 ? null : "PUSH_ALL_FAILED",
          now);
    }
  }

  private Instant completedOrNow(Instant completedAt, Instant now) {
    return completedAt == null ? now : completedAt;
  }

  private String safeCode(String code, String fallback) {
    return code == null || code.isBlank() ? fallback : code;
  }
}
