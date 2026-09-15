package com.messaging.media.service;

import lombok.RequiredArgsConstructor;

import java.util.logging.Logger;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MediaCleanupScheduler {

  private final MediaCleanupService mediaCleanupService;
  private final Logger logger = Logger.getLogger(MediaCleanupScheduler.class.getName());

  @Scheduled(initialDelayString = "${app.media.cleanup.initial-delay:1m}", fixedDelayString = "${app.media.cleanup.interval:12h}")
  public void cleanupExpiredPendingUploads() {
    logger.info("Starting cleanup of expired pending uploads");
    int deleted = mediaCleanupService.cleanupExpiredPendingUploads();
    logger.info("Finished cleanup of expired pending uploads. Deleted " + deleted + " items.");
  }
}
