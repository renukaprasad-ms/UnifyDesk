package com.messaging.lifecycle.service;

import com.messaging.lifecycle.config.DeletionLifecycleProperties;
import com.messaging.lifecycle.entity.DeletionPurgeJob;
import com.messaging.lifecycle.enums.DeletionPurgeStatus;
import com.messaging.lifecycle.enums.LifecycleEntityType;
import com.messaging.lifecycle.repository.DeletionPurgeJobRepository;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DeletionPurgeService {

  private final DeletionPurgeJobRepository repository;
  private final DeletionLifecycleProperties properties;

  @Transactional
  public DeletionPurgeJob schedule(
      LifecycleEntityType entityType,
      Long entityId,
      Long companyId,
      Long requestedByUserId,
      Instant purgeAfter) {
    DeletionPurgeJob job =
        repository.findByEntityTypeAndEntityId(entityType, entityId).orElseGet(DeletionPurgeJob::new);
    job.setEntityType(entityType);
    job.setEntityId(entityId);
    job.setCompanyId(companyId);
    job.setRequestedByUserId(requestedByUserId);
    job.setStatus(DeletionPurgeStatus.PENDING);
    job.setPurgeAfter(purgeAfter);
    job.setNextAttemptAt(null);
    job.setAttemptCount(0);
    job.setLastError(null);
    job.setClaimedAt(null);
    job.setCompletedAt(null);
    return repository.saveAndFlush(job);
  }

  @Transactional
  public void cancel(LifecycleEntityType entityType, Long entityId) {
    repository
        .findByEntityTypeAndEntityId(entityType, entityId)
        .ifPresent(
            job -> {
              if (job.getStatus() == DeletionPurgeStatus.COMPLETED
                  || job.getStatus() == DeletionPurgeStatus.CANCELLED) {
                return;
              }
              job.setStatus(DeletionPurgeStatus.CANCELLED);
              job.setNextAttemptAt(null);
              job.setClaimedAt(null);
              job.setLastError(null);
              repository.save(job);
            });
  }

  @Transactional
  public List<Long> claimDue() {
    int limit = Math.max(1, properties.getBatchSize());
    List<DeletionPurgeJob> jobs = repository.findDueForUpdate(limit);
    Instant now = Instant.now();
    for (DeletionPurgeJob job : jobs) {
      job.setStatus(DeletionPurgeStatus.RUNNING);
      job.setClaimedAt(now);
      job.setAttemptCount(job.getAttemptCount() + 1);
      job.setLastError(null);
      job.setNextAttemptAt(null);
    }
    repository.saveAll(jobs);
    return jobs.stream().map(DeletionPurgeJob::getId).toList();
  }

  @Transactional
  public DeletionPurgeJob require(Long jobId) {
    return repository
        .findById(jobId)
        .orElseThrow(() -> new IllegalStateException("Deletion purge job not found"));
  }

  @Transactional
  public void continueLater(Long jobId) {
    DeletionPurgeJob job =
        repository
            .findByIdForUpdate(jobId)
            .orElseThrow(() -> new IllegalStateException("Deletion purge job not found"));
    job.setStatus(DeletionPurgeStatus.PENDING);
    job.setClaimedAt(null);
    job.setNextAttemptAt(Instant.now());
    repository.save(job);
  }

  @Transactional
  public void complete(Long jobId) {
    DeletionPurgeJob job =
        repository
            .findByIdForUpdate(jobId)
            .orElseThrow(() -> new IllegalStateException("Deletion purge job not found"));
    if (job.getStatus() == DeletionPurgeStatus.CANCELLED) {
      return;
    }
    job.setStatus(DeletionPurgeStatus.COMPLETED);
    job.setCompletedAt(Instant.now());
    job.setClaimedAt(null);
    job.setNextAttemptAt(null);
    job.setLastError(null);
    repository.save(job);
  }

  @Transactional
  public void fail(Long jobId, Throwable error) {
    DeletionPurgeJob job =
        repository
            .findByIdForUpdate(jobId)
            .orElseThrow(() -> new IllegalStateException("Deletion purge job not found"));
    if (job.getStatus() == DeletionPurgeStatus.CANCELLED) {
      return;
    }
    job.setStatus(DeletionPurgeStatus.FAILED);
    job.setClaimedAt(null);
    job.setNextAttemptAt(Instant.now().plus(properties.getRetryDelay()));
    job.setLastError(truncate(error == null ? null : error.getMessage()));
    repository.save(job);
  }

  @Transactional
  public int recoverStale() {
    return repository.recoverStale(Instant.now().minus(properties.getStaleAfter()));
  }

  private String truncate(String value) {
    if (value == null || value.length() <= 1000) return value;
    return value.substring(0, 1000);
  }
}
