package com.messaging.notification.platform.delivery.model;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;

public final class DeliveryModels {

  private DeliveryModels() {}

  public enum Channel {
    EMAIL,
    WEB_PUSH
  }

  public enum DeliveryMode {
    IMMEDIATE,
    DELAYED,
    DIGEST
  }

  public enum AttemptOutcome {
    ACCEPTED,
    TRANSIENT_FAILURE,
    PERMANENT_FAILURE,
    RATE_LIMITED,
    UNKNOWN,
    SUPPRESSED_POLICY
  }

  public enum PushAttemptState {
    PENDING,
    CLAIMED,
    ACCEPTED,
    FAILED_RETRYABLE,
    FAILED_PERMANENT,
    DEAD,
    UNKNOWN
  }

  public record DeliveryIntent(
      long id,
      long notificationEventId,
      Long notificationId,
      Long companyId,
      long userId,
      String notificationType,
      String requiredPermissionKey,
      Channel channel,
      DeliveryMode deliveryMode,
      String idempotencyKey,
      String state,
      Instant notBefore,
      Instant nextAttemptAt,
      int attemptCount,
      String claimToken,
      Instant claimExpiresAt,
      Instant createdAt) {}

  public record RecipientSnapshot(
      long userId,
      String email,
      String userName,
      boolean active,
      boolean verified,
      Long companyId,
      String companyName,
      String companyTimezone,
      boolean companyEligible,
      boolean activeMember) {}

  public record EffectivePreference(String emailMode, boolean browserEnabled) {}

  public record QuietHours(
      String timezone, boolean enabled, LocalTime start, LocalTime end) {}

  public record RenderedEmail(String subject, String textBody, String htmlBody) {}

  public record PushSubscription(
      long id,
      long userId,
      String endpointHash,
      String endpointCiphertext,
      String p256dhCiphertext,
      String authCiphertext,
      Instant expirationTime,
      String deviceLabel,
      String status,
      Instant createdAt,
      Instant lastSuccessAt,
      Instant lastFailureAt,
      Instant revokedAt,
      Instant updatedAt) {}

  public record DigestBucket(
      long id,
      long userId,
      long companyId,
      String notificationType,
      String state,
      int contributionCount,
      Instant firstContributionAt,
      Instant lastContributionAt,
      Instant eligibleAt,
      Instant nextAttemptAt,
      String claimToken,
      Instant claimExpiresAt,
      Instant sentAt,
      Instant suppressedAt,
      String lastErrorCode) {}

  public record PushAttempt(
      long id,
      long intentId,
      long subscriptionId,
      int attemptNo,
      PushAttemptState state,
      Instant nextAttemptAt,
      String claimToken,
      Instant claimExpiresAt,
      Integer lastHttpStatus,
      String lastErrorCode,
      Instant acceptedAt) {}

  public record DigestContribution(
      DeliveryIntent intent, Instant contributionAt) {}

  public record DigestItemState(List<Long> intentIds, boolean hasUnseenUnreadNotification) {}
}
