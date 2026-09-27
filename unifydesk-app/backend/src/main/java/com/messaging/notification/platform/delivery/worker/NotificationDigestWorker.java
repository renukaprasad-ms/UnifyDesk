package com.messaging.notification.platform.delivery.worker;

import com.messaging.company.iam.CompanyPermissionKeys;
import com.messaging.company.service.CompanyIamPermissionService;
import com.messaging.notification.platform.delivery.config.NotificationDeliveryProperties;
import com.messaging.notification.platform.delivery.email.NotificationEmailRenderer;
import com.messaging.notification.platform.delivery.email.ProductEmailGateway;
import com.messaging.notification.platform.delivery.email.ProductEmailGateway.EmailSendResult;
import com.messaging.notification.platform.delivery.metrics.NotificationDeliveryMetrics;
import com.messaging.notification.platform.delivery.model.DeliveryModels.DeliveryIntent;
import com.messaging.notification.platform.delivery.model.DeliveryModels.DigestBucket;
import com.messaging.notification.platform.delivery.model.DeliveryModels.DigestItemState;
import com.messaging.notification.platform.delivery.model.DeliveryModels.EffectivePreference;
import com.messaging.notification.platform.delivery.model.DeliveryModels.QuietHours;
import com.messaging.notification.platform.delivery.model.DeliveryModels.RecipientSnapshot;
import com.messaging.notification.platform.delivery.policy.DeliveryEligibilityService;
import com.messaging.notification.platform.delivery.policy.NotificationDeliveryPolicy;
import com.messaging.notification.platform.delivery.repository.NotificationDeliveryRuntimeRepository;
import com.messaging.notification.platform.delivery.repository.NotificationDeliverySupportRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NotificationDigestWorker {

  private final NotificationDeliveryRuntimeRepository repository;
  private final NotificationDeliverySupportRepository supportRepository;
  private final DeliveryEligibilityService eligibilityService;
  private final NotificationDeliveryPolicy policy;
  private final NotificationDeliveryProperties properties;
  private final CompanyIamPermissionService permissionService;
  private final ProductEmailGateway emailGateway;
  private final NotificationEmailRenderer emailRenderer;
  private final NotificationDeliveryMetrics metrics;
  private final Clock clock = Clock.systemUTC();

  @Scheduled(fixedDelayString = "${app.notification-platform.digest.poll-delay:1m}")
  public void aggregateAndSend() {
    if (!properties.getDigest().isEnabled()) {
      return;
    }
    supportRepository.recoverAmbiguousDigestClaims();
    aggregate();
    sendDue();
  }

  void aggregate() {
    Instant now = clock.instant();
    List<DeliveryIntent> intents =
        repository.findUnaggregatedDigestIntents(properties.getDigest().getAggregationBatchSize());
    for (DeliveryIntent intent : intents) {
      if (!eligibilityService.eligibleDigestContribution(intent)) {
        supportRepository.suppressUnclaimedIntent(
            intent.id(), "DIGEST_CONTRIBUTION_INELIGIBLE", now);
        continue;
      }
      DigestBucket existing =
          supportRepository
              .activeDigestBucket(intent.userId(), intent.companyId(), intent.notificationType())
              .orElse(null);
      if (existing != null && "CLAIMED".equals(existing.state())) {
        continue;
      }
      Instant contributionAt = intent.createdAt() == null ? now : intent.createdAt();
      Instant eligibleAt =
          existing == null
              ? policy.digestEligibleAt(contributionAt, contributionAt, 1)
              : policy.recomputeDigestEligibleAt(existing, contributionAt);
      supportRepository.addDigestContributionSafely(intent, eligibleAt, contributionAt);
    }
  }

  void sendDue() {
    Instant now = clock.instant();
    List<DigestBucket> buckets =
        repository.claimDueDigestBuckets(
            properties.getDelivery().getClaimBatchSize(),
            now.plus(properties.getDelivery().getClaimLease()));
    for (DigestBucket bucket : buckets) {
      sendBucket(bucket, clock.instant());
    }
  }

  void sendBucket(DigestBucket bucket, Instant now) {
    RecipientSnapshot recipient =
        repository.recipientSnapshot(bucket.userId(), bucket.companyId()).orElse(null);
    if (recipient == null || !recipient.active()) {
      suppress(bucket, "USER_INACTIVE", now);
      return;
    }
    if (!recipient.companyEligible() || !recipient.activeMember()) {
      suppress(bucket, "MEMBERSHIP_REVOKED", now);
      return;
    }
    if (!permissionService.hasPermission(
        bucket.userId(), bucket.companyId(), CompanyPermissionKeys.EMAIL_READ)) {
      suppress(bucket, "PERMISSION_REVOKED", now);
      return;
    }

    EffectivePreference preference =
        repository.effectivePreference(
            bucket.userId(), bucket.companyId(), bucket.notificationType());
    if (!"DIGEST".equals(preference.emailMode())) {
      suppress(bucket, "DIGEST_PREFERENCE_DISABLED", now);
      return;
    }
    if (!recipient.verified() || recipient.email() == null || recipient.email().isBlank()) {
      suppress(bucket, "EMAIL_NOT_VERIFIED_OR_INVALID", now);
      return;
    }

    QuietHours quietHours =
        repository.quietHours(bucket.userId(), bucket.companyId(), recipient.companyTimezone());
    Instant quietEnd = policy.quietHoursDeferral(now, quietHours);
    if (quietEnd != null) {
      repository.deferDigest(bucket.id(), bucket.claimToken(), "QUIET_HOURS", quietEnd);
      return;
    }

    Instant previous =
        repository.lastSuccessfulDigestAt(
            bucket.userId(), bucket.companyId(), bucket.notificationType());
    if (previous != null) {
      Instant cooldownEnd = previous.plus(properties.getDigest().getCooldown());
      if (cooldownEnd.isAfter(now)) {
        repository.deferDigest(bucket.id(), bucket.claimToken(), "DIGEST_COOLDOWN", cooldownEnd);
        return;
      }
    }

    Instant rollingStart = now.minus(properties.getDigest().getRollingWindow());
    if (repository.acceptedOptionalEmailsSince(bucket.userId(), rollingStart)
        >= properties.getDigest().getOptionalEmailRollingCap()) {
      repository.deferDigest(
          bucket.id(),
          bucket.claimToken(),
          "OPTIONAL_EMAIL_ROLLING_CAP",
          now.plusSeconds(60));
      return;
    }

    long unread = repository.authoritativeInboxUnreadCount(bucket.companyId());
    DigestItemState itemState = repository.digestItemState(bucket.id());
    if (unread <= 0) {
      suppress(bucket, "ZERO_RELEVANT_UNREAD", now);
      return;
    }
    if (!itemState.hasUnseenUnreadNotification()) {
      suppress(bucket, "ACTIVITY_ALREADY_SEEN_OR_READ", now);
      return;
    }
    if (itemState.intentIds().isEmpty()) {
      suppress(bucket, "EMPTY_DIGEST", now);
      return;
    }

    long representativeIntent = itemState.intentIds().getFirst();
    int attemptNo = supportRepository.nextEmailAttemptNo(representativeIntent);
    Instant started = clock.instant();
    long attemptId =
        supportRepository.beginProviderAttempt(
            representativeIntent, null, attemptNo, "EMAIL", started);
    EmailSendResult result =
        emailGateway.send(
            recipient.email(), emailRenderer.inboxDigest(recipient.companyName(), unread));
    Instant completed = clock.instant();
    String outcome = mapOutcome(result);
    supportRepository.completeProviderAttempt(
        attemptId,
        completed,
        outcome,
        null,
        result.errorCode(),
        completed.toEpochMilli() - started.toEpochMilli());
    metrics.attempt("EMAIL", outcome);

    switch (result.outcome()) {
      case ACCEPTED -> {
        repository.markDigestSent(bucket.id(), bucket.claimToken(), completed);
        metrics.digestSent(bucket.notificationType());
      }
      case TRANSIENT_FAILURE -> {
        if (policy.emailAttemptsExhausted(attemptNo)) {
          supportRepository.terminalDigestAndIntents(
              bucket.id(), bucket.claimToken(), "DEAD", result.errorCode(), completed);
        } else {
          repository.retryDigest(
              bucket.id(),
              bucket.claimToken(),
              result.errorCode(),
              policy.emailRetryAt(completed, attemptNo));
        }
      }
      case PERMANENT_FAILURE ->
          supportRepository.terminalDigestAndIntents(
              bucket.id(),
              bucket.claimToken(),
              "DEAD",
              result.errorCode(),
              completed);
      case UNKNOWN -> {
        supportRepository.terminalDigestAndIntents(
            bucket.id(), bucket.claimToken(), "UNKNOWN", result.errorCode(), completed);
        metrics.emailUnknown();
      }
    }
  }

  private void suppress(DigestBucket bucket, String reason, Instant now) {
    repository.markDigestSuppressed(bucket.id(), bucket.claimToken(), reason, now);
    metrics.digestSuppressed(reason);
  }

  private String mapOutcome(EmailSendResult result) {
    return switch (result.outcome()) {
      case ACCEPTED -> "ACCEPTED";
      case TRANSIENT_FAILURE -> "TRANSIENT_FAILURE";
      case PERMANENT_FAILURE -> "PERMANENT_FAILURE";
      case UNKNOWN -> "UNKNOWN";
    };
  }
}
