package com.messaging.notification.platform.core.realtime;

import com.messaging.company.service.CompanyIamPermissionService;
import com.messaging.notification.platform.core.state.NotificationUnreadCounterService;
import com.messaging.realtime.service.RealtimeService;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationRealtimePublisher {

  private final RealtimeService realtimeService;
  private final NotificationUnreadCounterService counterService;
  private final CompanyIamPermissionService permissionService;

  public void afterCommit(long userId, Long companyId, Long notificationId, String reason) {
    Runnable publish = () -> publish(userId, companyId, notificationId, reason);
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              publish.run();
            }
          });
    } else {
      publish.run();
    }
  }

  private void publish(long userId, Long companyId, Long notificationId, String reason) {
    try {
      Set<String> permissions =
          companyId == null
              ? Set.of()
              : permissionService.effectivePermissions(userId, companyId);
      NotificationUnreadCounterService.Counts counts =
          counterService.counts(userId, companyId, permissions);
      realtimeService.sendToAllUserSessions(
          userId,
          "notification.changed.v1",
          new ChangedPayload(
              reason,
              notificationId == null ? null : Long.toString(notificationId),
              companyId == null ? null : Long.toString(companyId),
              counts.account(),
              counts.company(),
              counts.total()));
    } catch (RuntimeException exception) {
      log.warn(
          "Notification realtime publish failed userId={} companyId={} reason={}",
          userId,
          companyId,
          reason);
    }
  }

  public record ChangedPayload(
      String reason,
      String notificationId,
      String companyId,
      long accountUnread,
      long companyUnread,
      long totalUnread) {}
}
