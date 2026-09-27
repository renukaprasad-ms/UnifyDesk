package com.messaging.notification.platform.core.cleanup;

import com.messaging.notification.platform.core.config.NotificationPlatformProperties;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NotificationCoreRetentionWorker {

  private static final int MAX_BATCHES_PER_RUN = 8;

  private final NotificationCoreCleanupService cleanupService;
  private final NotificationPlatformProperties properties;

  @Scheduled(
      fixedDelayString = "${app.notification-platform.retention.cleanup-interval-ms:86400000}",
      initialDelayString = "${app.notification-platform.retention.cleanup-initial-delay-ms:300000}")
  public void cleanup() {
    int batchSize = Math.max(1, properties.getRetention().getCleanupBatchSize());
    Instant now = Instant.now();

    for (int batch = 0; batch < MAX_BATCHES_PER_RUN; batch++) {
      int deleted = cleanupService.cleanupNotificationHistoryBatch(now, batchSize);
      if (deleted < batchSize) {
        break;
      }
    }

    for (int batch = 0; batch < MAX_BATCHES_PER_RUN; batch++) {
      int deleted = cleanupService.cleanupEventDataBatch(now, batchSize);
      if (deleted < batchSize) {
        break;
      }
    }
  }
}
