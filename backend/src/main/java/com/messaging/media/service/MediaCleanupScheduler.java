package com.messaging.media.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MediaCleanupScheduler {

  private final MediaCleanupService mediaCleanupService;

  @Scheduled(
      initialDelayString = "${app.media.cleanup.initial-delay:1m}",
      fixedDelayString = "${app.media.cleanup.interval:12h}")
  public void cleanupExpiredPendingUploads() {
    mediaCleanupService.cleanupExpiredPendingUploads();
  }
}
