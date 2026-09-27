package com.messaging.notification.platform.delivery.service;

import com.messaging.notification.platform.delivery.config.NotificationDeliveryProperties;
import com.messaging.notification.platform.delivery.repository.NotificationDeliveryCleanupRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NotificationDeliveryCleanupService {

  private final NotificationDeliveryCleanupRepository repository;
  private final NotificationDeliveryProperties properties;

  public boolean purgeUserBatch(long userId, int requestedBatchSize) {
    int batchSize = bounded(requestedBatchSize);

    if (repository.revokeUserPushSubscriptionsBatch(userId, batchSize) >= batchSize) {
      return false;
    }
    if (repository.deleteUserDeliveryIntentsBatch(userId, batchSize) >= batchSize) {
      return false;
    }
    if (repository.deleteUserDigestBucketsBatch(userId, batchSize) >= batchSize) {
      return false;
    }
    if (repository.deleteUserPushSubscriptionsBatch(userId, batchSize) >= batchSize) {
      return false;
    }
    return !repository.hasUserDeliveryData(userId);
  }

  public boolean purgeCompanyBatch(long companyId, int requestedBatchSize) {
    int batchSize = bounded(requestedBatchSize);

    if (repository.deleteCompanyDeliveryIntentsBatch(companyId, batchSize) >= batchSize) {
      return false;
    }
    if (repository.deleteCompanyDigestBucketsBatch(companyId, batchSize) >= batchSize) {
      return false;
    }
    return !repository.hasCompanyDeliveryData(companyId);
  }

  public RetentionCleanupResult cleanupRetentionBatch(Instant now) {
    int batchSize = bounded(properties.getRetention().getCleanupBatchSize());
    Instant normalAttemptCutoff = now.minus(properties.getRetention().getDeliveryAttempts());
    Instant exceptionalAttemptCutoff =
        now.minus(properties.getRetention().getExceptionalDeliveryAttempts());
    Instant normalPushAttemptCutoff = now.minus(properties.getRetention().getPushAttempts());
    Instant exceptionalPushAttemptCutoff =
        now.minus(properties.getRetention().getExceptionalPushAttempts());
    Instant digestCutoff = now.minus(properties.getRetention().getDigestBuckets());
    Instant intentCutoff = now.minus(properties.getRetention().getDeliveryIntents());
    Instant subscriptionCutoff = now.minus(properties.getRetention().getPushSubscriptions());

    int deliveryAttempts =
        repository.deleteRetainedDeliveryAttemptsBatch(
            normalAttemptCutoff, exceptionalAttemptCutoff, batchSize);
    int pushAttempts =
        repository.deleteRetainedPushAttemptsBatch(
            normalPushAttemptCutoff, exceptionalPushAttemptCutoff, batchSize);
    int digestBuckets = repository.deleteRetainedDigestBucketsBatch(digestCutoff, batchSize);
    int deliveryIntents = repository.deleteRetainedDeliveryIntentsBatch(intentCutoff, batchSize);
    int subscriptions =
        repository.deleteRetainedPushSubscriptionsBatch(subscriptionCutoff, batchSize);

    return new RetentionCleanupResult(
        deliveryAttempts, pushAttempts, digestBuckets, deliveryIntents, subscriptions);
  }

  private int bounded(int requestedBatchSize) {
    int configured = Math.max(1, properties.getRetention().getCleanupBatchSize());
    return Math.max(1, Math.min(requestedBatchSize, configured));
  }

  public record RetentionCleanupResult(
      int deliveryAttempts,
      int pushAttempts,
      int digestBuckets,
      int deliveryIntents,
      int pushSubscriptions) {

    public int totalDeleted() {
      return deliveryAttempts
          + pushAttempts
          + digestBuckets
          + deliveryIntents
          + pushSubscriptions;
    }
  }
}
