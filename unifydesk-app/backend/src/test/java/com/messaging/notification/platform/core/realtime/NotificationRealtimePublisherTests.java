package com.messaging.notification.platform.core.realtime;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.messaging.company.service.CompanyIamPermissionService;
import com.messaging.notification.platform.core.state.NotificationUnreadCounterService;
import com.messaging.realtime.service.RealtimeService;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class NotificationRealtimePublisherTests {

  @AfterEach
  void cleanupSynchronization() {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.clearSynchronization();
    }
  }

  @Test
  void emitsOnlyAfterCommitWhenTransactionSynchronizationIsActive() {
    RealtimeService realtime = org.mockito.Mockito.mock(RealtimeService.class);
    NotificationUnreadCounterService counters =
        org.mockito.Mockito.mock(NotificationUnreadCounterService.class);
    CompanyIamPermissionService permissions =
        org.mockito.Mockito.mock(CompanyIamPermissionService.class);
    when(permissions.effectivePermissions(7L, 9L)).thenReturn(Set.of("email.read"));
    when(counters.counts(7L, 9L, Set.of("email.read")))
        .thenReturn(new NotificationUnreadCounterService.Counts(2, 3, 5));
    NotificationRealtimePublisher publisher =
        new NotificationRealtimePublisher(realtime, counters, permissions);

    TransactionSynchronizationManager.initSynchronization();
    publisher.afterCommit(7L, 9L, 11L, "CREATED");

    verify(realtime, never())
        .sendToAllUserSessions(
            org.mockito.ArgumentMatchers.anyLong(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.any());

    for (TransactionSynchronization synchronization :
        TransactionSynchronizationManager.getSynchronizations()) {
      synchronization.afterCommit();
    }

    verify(realtime)
        .sendToAllUserSessions(
            org.mockito.ArgumentMatchers.eq(7L),
            org.mockito.ArgumentMatchers.eq("notification.changed.v1"),
            org.mockito.ArgumentMatchers.any());
  }

  @Test
  void realtimeFailureIsIsolated() {
    RealtimeService realtime = org.mockito.Mockito.mock(RealtimeService.class);
    NotificationUnreadCounterService counters =
        org.mockito.Mockito.mock(NotificationUnreadCounterService.class);
    CompanyIamPermissionService permissions =
        org.mockito.Mockito.mock(CompanyIamPermissionService.class);
    when(counters.counts(7L, null, Set.of()))
        .thenReturn(new NotificationUnreadCounterService.Counts(1, 0, 1));
    org.mockito.Mockito.doThrow(new IllegalStateException("redis unavailable"))
        .when(realtime)
        .sendToAllUserSessions(
            org.mockito.ArgumentMatchers.anyLong(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.any());

    new NotificationRealtimePublisher(realtime, counters, permissions)
        .afterCommit(7L, null, 11L, "CREATED");
  }
}
