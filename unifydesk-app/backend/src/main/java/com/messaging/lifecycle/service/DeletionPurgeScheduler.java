package com.messaging.lifecycle.service;

import com.messaging.lifecycle.entity.DeletionPurgeJob;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeletionPurgeScheduler {

  private final DeletionPurgeService purgeService;
  private final EntityPurgeService entityPurgeService;

  @Scheduled(
      fixedDelayString = "${app.lifecycle.deletion.scheduler-interval:10m}",
      initialDelayString = "${app.lifecycle.deletion.scheduler-initial-delay:1m}")
  public void run() {
    int recovered = purgeService.recoverStale();
    if (recovered > 0) {
      log.warn("Recovered {} stale deletion purge jobs", recovered);
    }

    List<Long> jobs = purgeService.claimDue();
    for (Long jobId : jobs) {
      try {
        DeletionPurgeJob job = purgeService.require(jobId);
        boolean completed = entityPurgeService.purge(job);
        if (completed) {
          purgeService.complete(jobId);
        } else {
          purgeService.continueLater(jobId);
        }
      } catch (RuntimeException exception) {
        log.error("Deletion purge failed jobId={}", jobId, exception);
        purgeService.fail(jobId, exception);
      }
    }
  }
}
