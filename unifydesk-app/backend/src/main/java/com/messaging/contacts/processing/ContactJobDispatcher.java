package com.messaging.contacts.processing;

import com.messaging.asyncjob.enums.AsyncJobType;
import com.messaging.asyncjob.service.AsyncJobService;
import com.messaging.contacts.audience.AudienceSnapshotService;
import com.messaging.contacts.audience.ContactAudienceProperties;
import com.messaging.contacts.audience.SegmentService;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ContactJobDispatcher {

  private final AsyncJobService asyncJobs;
  private final ContactProcessingAuthorizationPolicy authorizationPolicy;
  private final ContactImportWorker importWorker;
  private final ContactExportWorker exportWorker;
  private final ContactBulkWorker bulkWorker;
  private final ContactDeletePurgeWorker deletePurgeWorker;
  private final ContactImportRepository importRepository;
  private final ContactExportRepository exportRepository;
  private final ContactBulkRepository bulkRepository;
  private final SegmentService segmentService;
  private final AudienceSnapshotService snapshotService;
  private final ContactAudienceProperties properties;
  private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
  private final AtomicInteger inFlight = new AtomicInteger();

  public ContactJobDispatcher(
      AsyncJobService asyncJobs,
      ContactProcessingAuthorizationPolicy authorizationPolicy,
      ContactImportWorker importWorker,
      ContactExportWorker exportWorker,
      ContactBulkWorker bulkWorker,
      ContactDeletePurgeWorker deletePurgeWorker,
      ContactImportRepository importRepository,
      ContactExportRepository exportRepository,
      ContactBulkRepository bulkRepository,
      SegmentService segmentService,
      AudienceSnapshotService snapshotService,
      ContactAudienceProperties properties) {
    this.asyncJobs = asyncJobs;
    this.authorizationPolicy = authorizationPolicy;
    this.importWorker = importWorker;
    this.exportWorker = exportWorker;
    this.bulkWorker = bulkWorker;
    this.deletePurgeWorker = deletePurgeWorker;
    this.importRepository = importRepository;
    this.exportRepository = exportRepository;
    this.bulkRepository = bulkRepository;
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
            AsyncJobType.CONTACT_BULK,
            AsyncJobType.CONTACT_DELETE_PURGE,
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
                processClaimedJob(type, job);
              } catch (RuntimeException exception) {
                asyncJobs.fail(job.jobId(), exception.getClass().getSimpleName());
              } finally {
                inFlight.decrementAndGet();
              }
            });
      }
    }
  }

  void processClaimedJob(
      AsyncJobType type, AsyncJobService.ClaimedAsyncJob job) {
    ContactProcessingAuthorizationPolicy.Decision decision =
        authorizationPolicy.authorizeClaim(type, job);
    if (!decision.allowed()) {
      failDeniedResource(type, job);
      asyncJobs.fail(job.jobId(), decision.failureCode());
      return;
    }
    processAuthorized(type, job);
  }


  private void failDeniedResource(
      AsyncJobType type, AsyncJobService.ClaimedAsyncJob job) {
    if (job == null || job.companyId() == null || job.resourceId() == null) {
      return;
    }
    final long resourceId;
    try {
      resourceId = Long.parseLong(job.resourceId());
    } catch (NumberFormatException exception) {
      return;
    }
    switch (type) {
      case CONTACT_IMPORT -> importRepository.fail(job.companyId(), resourceId);
      case CONTACT_EXPORT -> exportRepository.fail(job.companyId(), resourceId);
      case CONTACT_BULK -> bulkRepository.fail(job.companyId(), resourceId);
      default -> {
        // Non user-owned resource types have their own lifecycle semantics.
      }
    }
  }

  private void processAuthorized(
      AsyncJobType type, AsyncJobService.ClaimedAsyncJob job) {
    if (job.resourceId() == null) {
      throw new IllegalStateException("Contacts job has no resource");
    }
    long resourceId = Long.parseLong(job.resourceId());
    switch (type) {
      case CONTACT_IMPORT -> importWorker.process(job.companyId(), resourceId, job.jobId());
      case CONTACT_EXPORT -> exportWorker.process(job.companyId(), resourceId, job.jobId());
      case CONTACT_BULK -> bulkWorker.process(job.companyId(), resourceId, job.jobId());
      case CONTACT_DELETE_PURGE -> {
        asyncJobs.startOrResume(job.jobId(), 1, "Contact privacy purge running");
        deletePurgeWorker.process(job.companyId(), resourceId);
        asyncJobs.complete(job.jobId(), "Contact privacy purge completed");
      }
      case CONTACT_SEGMENT_COUNT -> {
        asyncJobs.startOrResume(job.jobId(), 1, "Segment count running");
        segmentService.processCountJob(job.companyId(), resourceId);
        asyncJobs.complete(job.jobId(), "Segment count completed");
      }
      case CONTACT_AUDIENCE_SNAPSHOT -> {
        asyncJobs.startOrResume(job.jobId(), 0, "Audience snapshot running");
        while (snapshotService.processBatch(job.companyId(), resourceId)) {
          // Each iteration is bounded by the configured keyset batch size.
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
