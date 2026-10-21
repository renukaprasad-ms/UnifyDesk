package com.messaging.notification.platform.core.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.messaging.notification.platform.core.config.NotificationPlatformProperties;
import com.messaging.notification.platform.core.entity.NotificationEvent;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class NotificationOutboxRepositoryTests {
  @Test
  void claimedEntityGetsLeaseAndAttemptWhileSnapshotRetainsPreviousAttemptCount() {
    NotificationEventJpaRepository jpa = mock(NotificationEventJpaRepository.class);
    NotificationPlatformProperties properties = new NotificationPlatformProperties();
    NotificationEvent event = new NotificationEvent();
    event.setId(42L);
    event.setEventKey("company.created:9");
    event.setPublishState("PENDING");
    event.setPublishAttempts(2);
    when(jpa.claimDue(any(), anyInt())).thenReturn(List.of(event));

    var claimed = new NotificationOutboxRepository(jpa, properties).claimDue().getFirst();

    assertThat(event.getPublishState()).isEqualTo("CLAIMED");
    assertThat(event.getPublishAttempts()).isEqualTo(3);
    assertThat(claimed.row().publishAttempts()).isEqualTo(2);
    assertThat(claimed.token()).isEqualTo(event.getClaimToken()).isNotBlank();
    assertThat(event.getClaimExpiresAt()).isAfter(event.getUpdatedAt());
  }

  @Test
  void outcomesUseClaimTokenToProtectAgainstExpiredWorkers() {
    NotificationEventJpaRepository jpa = mock(NotificationEventJpaRepository.class);
    NotificationOutboxRepository repository =
        new NotificationOutboxRepository(jpa, new NotificationPlatformProperties());
    var row =
        new NotificationOutboxRepository.EventRow(
            42L,
            "key",
            "company.created",
            1,
            9L,
            7L,
            "correlation",
            null,
            "MEDIUM",
            Instant.now(),
            2);
    var claim = new NotificationOutboxRepository.ClaimedEvent(row, "lease-token");
    Instant next = Instant.now().plusSeconds(60);

    repository.markPublished(claim);
    repository.markFailed(claim, false, next);
    repository.markFailed(claim, true, next);

    verify(jpa).markPublished(eq(42L), eq("lease-token"), any());
    verify(jpa).markFailed(eq(42L), eq("lease-token"), eq("PENDING"), eq(next), any());
    verify(jpa).markFailed(eq(42L), eq("lease-token"), eq("DEAD"), eq(next), any());
  }
}
