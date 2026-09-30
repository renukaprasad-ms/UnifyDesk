package com.messaging.contacts.processing;

import com.messaging.asyncjob.enums.AsyncJobType;
import com.messaging.asyncjob.service.AsyncJobService;
import com.messaging.contacts.audience.*;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ContactJobDispatcher {

  private final AsyncJobService asyncJobs;
  private final ContactImportWorker importWorker;
  private final ContactExportWorker exportWorker;
  private final SegmentService segmentService;
  private final AudienceSnapshotService snapshotService;
  private final ContactAudienceProperties properties;
  private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
  private final AtomicInteger inFlight = new AtomicInteger();

  public ContactJobDispatcher(
      AsyncJobService asyncJobs,
      ContactImportWorker importWorker,
      ContactExportWorker exportWorker,
      SegmentService segmentService,
      AudienceSnapshotService snapshotService,
      ContactAudienceProperties properties) {
    this.asyncJobs = asyncJobs;
    this.importWorker = importWorker;
    this.exportWorker = exportWorker;
    this.segmentService = segmentService;
    this.snapshotService = snapshotService;
    this.properties = properties;
  }

  @Scheduled(fixedDelayString = "${app.contacts.worker-poll-interval-ms:500}")
  public void dispatch() {
    int available = Math.max(1, properties.getWorkerConcurrency()) - inFlight.get();
    if (available <= 0) return;
    for (AsyncJobType type :
        List.of(
            AsyncJobType.CONTACT_IMPORT,
            AsyncJobType.CONTACT_EXPORT,
            AsyncJobType.CONTACT_SEGMENT_COUNT,
            AsyncJobType.CONTACT_AUDIENCE_SNAPSHOT)) {
      if (available <= 0) break;
      List<AsyncJobService.ClaimedAsyncJob> jobs =
          asyncJobs.claim(type, available, properties.getWorkerStaleAfter());
      for (AsyncJobService.ClaimedAsyncJob job : jobs) {
        available--;
        inFlight.incrementAndGet();
        executor.submit(
            () -> {
              try {
                process(type, job);
              } catch (RuntimeException exception) {
                asyncJobs.fail(job.jobId(), exception.getClass().getSimpleName());
              } finally {
                inFlight.decrementAndGet();
              }
            });
      }
    }
  }

  private void process(AsyncJobType type, AsyncJobService.ClaimedAsyncJob job) {
    if (job.resourceId() == null) throw new IllegalStateException("Contacts job has no resource");
    long resourceId = Long.parseLong(job.resourceId());
    switch (type) {
      case CONTACT_IMPORT -> importWorker.process(job.companyId(), resourceId, job.jobId());
      case CONTACT_EXPORT -> exportWorker.process(job.companyId(), resourceId, job.jobId());
      case CONTACT_SEGMENT_COUNT -> {
        asyncJobs.startOrResume(job.jobId(), 1, "Segment count running");
        segmentService.processCountJob(job.companyId(), resourceId);
        asyncJobs.complete(job.jobId(), "Segment count completed");
      }
      case CONTACT_AUDIENCE_SNAPSHOT -> {
        asyncJobs.startOrResume(job.jobId(), 0, "Audience snapshot running");
        while (snapshotService.processBatch(job.companyId(), resourceId)) {
          // Each iteration commits one bounded keyset batch.
        }
      }
      default -> throw new IllegalStateException("Unsupported contacts job type " + type);
    }
  }

  @PreDestroy
  void shutdown() {
    executor.shutdown();
  }
}
