package com.messaging.asyncjob.service;

import com.messaging.asyncjob.dto.AsyncJobResponse;
import com.messaging.asyncjob.entity.AsyncJob;
import com.messaging.asyncjob.enums.AsyncJobStatus;
import com.messaging.asyncjob.enums.AsyncJobType;
import com.messaging.asyncjob.repository.AsyncJobRepository;
import com.messaging.common.exception.NotFoundException;
import com.messaging.realtime.service.RealtimeService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AsyncJobService {

  public static final String PROGRESS_EVENT = "async.job.progress";

  private final AsyncJobRepository repository;
  private final RealtimeService realtimeService;

  @Transactional
  public AsyncJobResponse create(
      Long companyId, Long userId, AsyncJobType jobType, String metadataJson, String message) {
    return create(companyId, userId, jobType, null, metadataJson, message);
  }

  @Transactional
  public AsyncJobResponse create(
      Long companyId,
      Long userId,
      AsyncJobType jobType,
      String resourceId,
      String metadataJson,
      String message) {
    AsyncJob job = new AsyncJob();
    job.setCompanyId(companyId);
    job.setUserId(userId);
    job.setJobType(jobType);
    job.setResourceId(resourceId);
    job.setStatus(AsyncJobStatus.QUEUED);
    job.setStatusMessage(message);
    job.setMetadataJson(metadataJson);
    job = repository.saveAndFlush(job);
    AsyncJobResponse response = toResponse(job);
    realtimeService.sendToAllUserSessions(userId, PROGRESS_EVENT, response);
    return response;
  }

  @Transactional
  public AsyncJobResponse start(Long jobId, long totalUnits, String message) {
    AsyncJob job = require(jobId);
    job.setStatus(AsyncJobStatus.RUNNING);
    job.setStartedAt(job.getStartedAt() == null ? Instant.now() : job.getStartedAt());
    job.setTotalUnits(Math.max(totalUnits, 0));
    job.setCompletedUnits(0);
    job.setProgressPercent(0);
    job.setStatusMessage(message);
    job.setErrorMessage(null);
    return saveAndPublish(job);
  }

  @Transactional
  public AsyncJobResponse progress(
      Long jobId, long completedUnits, long totalUnits, String message) {
    AsyncJob job = require(jobId);
    long total = Math.max(totalUnits, 0);
    long completed = Math.max(0, Math.min(completedUnits, total == 0 ? completedUnits : total));
    job.setStatus(AsyncJobStatus.RUNNING);
    job.setTotalUnits(total);
    job.setCompletedUnits(completed);
    job.setProgressPercent(total <= 0 ? 0 : (int) Math.min(99, (completed * 100) / total));
    job.setStatusMessage(message);
    return saveAndPublish(job);
  }

  @Transactional
  public AsyncJobResponse complete(Long jobId, String message) {
    AsyncJob job = require(jobId);
    job.setStatus(AsyncJobStatus.COMPLETED);
    job.setCompletedUnits(job.getTotalUnits());
    job.setProgressPercent(100);
    job.setStatusMessage(message);
    job.setCompletedAt(Instant.now());
    return saveAndPublish(job);
  }

  @Transactional
  public AsyncJobResponse fail(Long jobId, String error) {
    AsyncJob job = require(jobId);
    job.setStatus(AsyncJobStatus.FAILED);
    job.setStatusMessage("Failed");
    job.setErrorMessage(truncate(error, 1000));
    job.setCompletedAt(Instant.now());
    return saveAndPublish(job);
  }

  @Transactional(readOnly = true)
  public Optional<AsyncJobResponse> findActive(
      Long companyId, AsyncJobType jobType, String resourceId) {
    return repository
        .findFirstByCompanyIdAndJobTypeAndResourceIdAndStatusInOrderByCreatedAtDesc(
            companyId,
            jobType,
            resourceId,
            List.of(AsyncJobStatus.QUEUED, AsyncJobStatus.RUNNING))
        .map(this::toResponse);
  }

  @Transactional
  public AsyncJobResponse setTotal(Long jobId, long totalUnits, String message) {
    AsyncJob job = requireForUpdate(jobId);
    long total = Math.max(totalUnits, 0);
    job.setStatus(AsyncJobStatus.RUNNING);
    job.setStartedAt(job.getStartedAt() == null ? Instant.now() : job.getStartedAt());
    job.setTotalUnits(total);
    if (job.getCompletedUnits() > total && total > 0) {
      job.setCompletedUnits(total);
    }
    job.setProgressPercent(
        total <= 0
            ? 0
            : (int) Math.min(99, (Math.max(0, job.getCompletedUnits()) * 100) / total));
    job.setStatusMessage(message);
    return saveAndPublish(job);
  }

  @Transactional
  public AsyncJobResponse incrementProgress(Long jobId, long delta, String message) {
    AsyncJob job = requireForUpdate(jobId);
    long completed = Math.max(0, job.getCompletedUnits() + Math.max(delta, 0));
    long total = Math.max(job.getTotalUnits(), completed);
    job.setStatus(AsyncJobStatus.RUNNING);
    job.setStartedAt(job.getStartedAt() == null ? Instant.now() : job.getStartedAt());
    job.setTotalUnits(total);
    job.setCompletedUnits(completed);
    job.setProgressPercent(
        total <= 0 ? 0 : (int) Math.min(99, (completed * 100) / Math.max(1, total)));
    job.setStatusMessage(message);
    return saveAndPublish(job);
  }

  @Transactional(readOnly = true)
  public AsyncJobResponse getForUser(Long jobId, Long userId) {
    return repository
        .findByIdAndUserId(jobId, userId)
        .map(this::toResponse)
        .orElseThrow(() -> new NotFoundException("Async job not found"));
  }

  private AsyncJob requireForUpdate(Long jobId) {
    return repository
        .findByIdForUpdate(jobId)
        .orElseThrow(() -> new NotFoundException("Async job not found"));
  }

  private AsyncJob require(Long jobId) {
    return repository
        .findById(jobId)
        .orElseThrow(() -> new NotFoundException("Async job not found"));
  }

  private AsyncJobResponse saveAndPublish(AsyncJob job) {
    AsyncJobResponse response = toResponse(repository.saveAndFlush(job));
    realtimeService.sendToAllUserSessions(job.getUserId(), PROGRESS_EVENT, response);
    return response;
  }

  private AsyncJobResponse toResponse(AsyncJob job) {
    return new AsyncJobResponse(
        job.getId().toString(),
        job.getCompanyId() == null ? null : job.getCompanyId().toString(),
        job.getUserId().toString(),
        job.getJobType(),
        job.getStatus(),
        job.getTotalUnits(),
        job.getCompletedUnits(),
        job.getProgressPercent(),
        job.getStatusMessage(),
        job.getErrorMessage(),
        job.getCreatedAt(),
        job.getStartedAt(),
        job.getCompletedAt(),
        job.getUpdatedAt());
  }

  private String truncate(String value, int maxLength) {
    if (value == null || value.length() <= maxLength) return value;
    return value.substring(0, maxLength);
  }
}
