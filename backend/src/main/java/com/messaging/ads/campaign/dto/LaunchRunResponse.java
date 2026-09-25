package com.messaging.ads.campaign.dto;

import com.messaging.ads.campaign.launch.LaunchRun;
import com.messaging.ads.campaign.launch.LaunchRunStatus;
import com.messaging.ads.campaign.launch.LaunchStep;
import java.time.Instant;
import java.util.List;

public record LaunchRunResponse(
    String id,
    String companyId,
    String campaignId,
    String idempotencyKey,
    LaunchRunStatus status,
    String requestedByUserId,
    String validationIssuesJson,
    String lastErrorCode,
    String lastErrorMessage,
    Instant startedAt,
    Instant completedAt,
    List<LaunchStepResponse> steps,
    Instant createdAt,
    Instant updatedAt) {

  public static LaunchRunResponse from(LaunchRun run, List<LaunchStep> steps) {
    return new LaunchRunResponse(
        run.getId().toString(),
        run.getCompanyId().toString(),
        run.getReachlyoCampaignId().toString(),
        run.getIdempotencyKey(),
        run.getStatus(),
        run.getRequestedByUserId().toString(),
        run.getValidationIssuesJson(),
        run.getLastErrorCode(),
        run.getLastErrorMessage(),
        run.getStartedAt(),
        run.getCompletedAt(),
        steps.stream().map(LaunchStepResponse::from).toList(),
        run.getCreatedAt(),
        run.getUpdatedAt());
  }
}
