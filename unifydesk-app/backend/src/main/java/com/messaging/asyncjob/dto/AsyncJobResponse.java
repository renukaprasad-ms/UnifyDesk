package com.messaging.asyncjob.dto;

import com.messaging.asyncjob.enums.AsyncJobStatus;
import com.messaging.asyncjob.enums.AsyncJobType;
import java.time.Instant;

public record AsyncJobResponse(
    String jobId,
    String companyId,
    String userId,
    AsyncJobType jobType,
    AsyncJobStatus status,
    long totalUnits,
    long completedUnits,
    int progressPercent,
    String statusMessage,
    String errorMessage,
    Instant createdAt,
    Instant startedAt,
    Instant completedAt,
    Instant updatedAt) {}
