package com.messaging.notification.platform.delivery.worker;

import com.messaging.notification.platform.delivery.service.NotificationDeliveryCleanupService;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NotificationDeliveryRetentionWorker {

  private final NotificationDeliveryCleanupService cleanupService;

  @Scheduled(
      fixedDelayString = "${app.notification-platform.retention.cleanup-interval-ms:86400000}",
      initialDelayString = "${app.notification-platform.retention.cleanup-initial-delay-ms:300000}")
  public void cleanup() {
    cleanupService.cleanupRetentionBatch(Instant.now());
  }
}
