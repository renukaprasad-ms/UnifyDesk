package com.messaging.emailprovider.validation.dto;

import com.messaging.emailprovider.validation.entity.EmailValidationJob;
import com.messaging.emailprovider.validation.enums.ValidationJobStatus;
import java.time.Instant;

public record ValidationJobResponse(
    String id,
    String companyId,
    ValidationJobStatus status,
    long total,
    long duplicates,
    long processed,
    long valid,
    long invalid,
    long risky,
    long unknown,
    int totalBatches,
    int completedBatches,
    int failedBatches,
    Instant createdAt,
    Instant startedAt,
    Instant completedAt) {

  public static ValidationJobResponse from(EmailValidationJob job) {
    return new ValidationJobResponse(
        job.getId().toString(),
        job.getCompanyId().toString(),
        job.getStatus(),
        job.getTotalCount(),
        job.getDuplicateCount(),
        job.getProcessedCount(),
        job.getValidCount(),
        job.getInvalidCount(),
        job.getRiskyCount(),
        job.getUnknownCount(),
        job.getTotalBatches(),
        job.getCompletedBatches(),
        job.getFailedBatches(),
        job.getCreatedAt(),
        job.getStartedAt(),
        job.getCompletedAt());
  }
}
