package com.messaging.ads.campaign.dto;

import com.messaging.ads.campaign.compiler.ProviderPlanAction;
import com.messaging.ads.campaign.compiler.ProviderPlanResource;
import com.messaging.ads.campaign.launch.LaunchStep;
import com.messaging.ads.campaign.launch.LaunchStepStatus;
import com.messaging.ads.enums.AdProvider;
import java.time.Instant;

public record LaunchStepResponse(
    String id,
    String runId,
    String channelId,
    String deploymentId,
    String adAccountId,
    AdProvider provider,
    int sequence,
    String operationKey,
    ProviderPlanAction action,
    ProviderPlanResource resource,
    boolean createPaused,
    LaunchStepStatus status,
    int attemptCount,
    String localResourceId,
    String externalResourceId,
    String lastErrorCode,
    String lastErrorMessage,
    Instant startedAt,
    Instant completedAt,
    Instant createdAt,
    Instant updatedAt) {

  public static LaunchStepResponse from(LaunchStep step) {
    return new LaunchStepResponse(
        step.getId().toString(),
        step.getLaunchRunId().toString(),
        step.getCampaignChannelId().toString(),
        step.getProviderDeploymentId().toString(),
        step.getAdAccountId().toString(),
        step.getProvider(),
        step.getSequence(),
        step.getOperationKey(),
        step.getAction(),
        step.getResource(),
        step.isCreatePaused(),
        step.getStatus(),
        step.getAttemptCount(),
        step.getLocalResourceId() == null ? null : step.getLocalResourceId().toString(),
        step.getExternalResourceId(),
        step.getLastErrorCode(),
        step.getLastErrorMessage(),
        step.getStartedAt(),
        step.getCompletedAt(),
        step.getCreatedAt(),
        step.getUpdatedAt());
  }
}
