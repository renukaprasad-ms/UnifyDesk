package com.messaging.notification.platform.delivery.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.messaging.notification.platform.delivery.config.NotificationDeliveryProperties;
import com.messaging.notification.platform.delivery.repository.NotificationDeliveryCleanupRepository;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class NotificationDeliveryCleanupServiceTests {

  private static final Instant NOW = Instant.parse("2026-09-27T08:00:00Z");

  private NotificationDeliveryCleanupRepository repository;
  private NotificationDeliveryProperties properties;
  private NotificationDeliveryCleanupService service;

  @BeforeEach
  void setUp() {
    repository = Mockito.mock(NotificationDeliveryCleanupRepository.class);
    properties = new NotificationDeliveryProperties();
    service = new NotificationDeliveryCleanupService(repository, properties);
  }

  @Test
  void userPurgeRevokesActivePushCapabilityBeforeDeletingSecrets() {
    when(repository.revokeUserPushSubscriptionsBatch(7L, 10)).thenReturn(10);

    assertThat(service.purgeUserBatch(7L, 10)).isFalse();

    verify(repository).revokeUserPushSubscriptionsBatch(7L, 10);
    verify(repository, never()).deleteUserDeliveryIntentsBatch(7L, 10);
    verify(repository, never()).deleteUserPushSubscriptionsBatch(7L, 10);
  }

  @Test
  void repeatedUserPurgeIsIdempotentOnceDeliveryDataIsDrained() {
    when(repository.revokeUserPushSubscriptionsBatch(7L, 10)).thenReturn(0);
    when(repository.deleteUserDeliveryIntentsBatch(7L, 10)).thenReturn(0);
    when(repository.deleteUserDigestBucketsBatch(7L, 10)).thenReturn(0);
    when(repository.deleteUserPushSubscriptionsBatch(7L, 10)).thenReturn(0);
    when(repository.hasUserDeliveryData(7L)).thenReturn(false);

    assertThat(service.purgeUserBatch(7L, 10)).isTrue();
    assertThat(service.purgeUserBatch(7L, 10)).isTrue();
  }

  @Test
  void companyPurgeMakesBoundedProgress() {
    when(repository.deleteCompanyDeliveryIntentsBatch(9L, 25)).thenReturn(25);

    assertThat(service.purgeCompanyBatch(9L, 25)).isFalse();

    verify(repository).deleteCompanyDeliveryIntentsBatch(9L, 25);
    verify(repository, never()).deleteCompanyDigestBucketsBatch(9L, 25);
  }

  @Test
  void retentionUsesFrozenNormalAndExceptionalWindows() {
    properties.getRetention().setDeliveryAttempts(Duration.ofDays(30));
    properties.getRetention().setExceptionalDeliveryAttempts(Duration.ofDays(90));
    properties.getRetention().setPushAttempts(Duration.ofDays(30));
    properties.getRetention().setExceptionalPushAttempts(Duration.ofDays(90));
    properties.getRetention().setDigestBuckets(Duration.ofDays(30));
    properties.getRetention().setDeliveryIntents(Duration.ofDays(90));
    properties.getRetention().setPushSubscriptions(Duration.ofDays(30));
    properties.getRetention().setCleanupBatchSize(1000);

    service.cleanupRetentionBatch(NOW);

    verify(repository)
        .deleteRetainedDeliveryAttemptsBatch(
            eq(NOW.minus(Duration.ofDays(30))),
            eq(NOW.minus(Duration.ofDays(90))),
            eq(1000));
    verify(repository)
        .deleteRetainedPushAttemptsBatch(
            eq(NOW.minus(Duration.ofDays(30))),
            eq(NOW.minus(Duration.ofDays(90))),
            eq(1000));
    verify(repository)
        .deleteRetainedDigestBucketsBatch(eq(NOW.minus(Duration.ofDays(30))), eq(1000));
    verify(repository)
        .deleteRetainedDeliveryIntentsBatch(eq(NOW.minus(Duration.ofDays(90))), eq(1000));
    verify(repository)
        .deleteRetainedPushSubscriptionsBatch(eq(NOW.minus(Duration.ofDays(30))), eq(1000));
  }

  @Test
  void requestedBatchCannotExceedConfiguredCleanupBound() {
    properties.getRetention().setCleanupBatchSize(5);
    when(repository.revokeUserPushSubscriptionsBatch(7L, 5)).thenReturn(0);
    when(repository.deleteUserDeliveryIntentsBatch(7L, 5)).thenReturn(0);
    when(repository.deleteUserDigestBucketsBatch(7L, 5)).thenReturn(0);
    when(repository.deleteUserPushSubscriptionsBatch(7L, 5)).thenReturn(0);
    when(repository.hasUserDeliveryData(7L)).thenReturn(false);

    assertThat(service.purgeUserBatch(7L, 5000)).isTrue();

    verify(repository).revokeUserPushSubscriptionsBatch(7L, 5);
  }
}
