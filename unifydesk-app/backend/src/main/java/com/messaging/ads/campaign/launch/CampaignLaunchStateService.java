package com.messaging.ads.campaign.launch;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.ads.campaign.dto.LaunchRunResponse;
import com.messaging.ads.campaign.entity.ProviderDeployment;
import com.messaging.ads.campaign.entity.ReachlyoCampaign;
import com.messaging.ads.campaign.enums.ProviderDeploymentStatus;
import com.messaging.ads.campaign.enums.ReachlyoCampaignStatus;
import com.messaging.ads.campaign.repository.ProviderDeploymentRepository;
import com.messaging.ads.campaign.repository.ReachlyoCampaignRepository;
import com.messaging.common.exception.ConflictException;
import com.messaging.common.exception.NotFoundException;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CampaignLaunchStateService {

  private final LaunchRunRepository runRepository;
  private final LaunchStepRepository stepRepository;
  private final ProviderDeploymentRepository deploymentRepository;
  private final ReachlyoCampaignRepository campaignRepository;
  private final ObjectMapper objectMapper;

  @Transactional(readOnly = true)
  public LaunchRun requireRun(Long companyId, Long campaignId, Long runId) {
    return runRepository
        .findByIdAndCompanyId(runId, companyId)
        .filter(run -> run.getReachlyoCampaignId().equals(campaignId))
        .orElseThrow(() -> new NotFoundException("Campaign launch run not found"));
  }

  @Transactional(readOnly = true)
  public List<LaunchStep> steps(Long companyId, Long runId) {
    return stepRepository.findByCompanyIdAndLaunchRunIdOrderBySequenceAscIdAsc(companyId, runId);
  }

  @Transactional
  public LaunchRun beginRun(Long companyId, Long campaignId, Long runId) {
    LaunchRun run = requireRun(companyId, campaignId, runId);
    if (run.getStatus() == LaunchRunStatus.PAUSED_READY) return run;
    if (run.getStatus() == LaunchRunStatus.VALIDATING
        || run.getStatus() == LaunchRunStatus.VALIDATION_FAILED) {
      throw new ConflictException("Campaign launch run is not executable");
    }
    if (run.getStatus() != LaunchRunStatus.READY
        && run.getStatus() != LaunchRunStatus.FAILED
        && run.getStatus() != LaunchRunStatus.PARTIAL_FAILURE
        && run.getStatus() != LaunchRunStatus.RUNNING) {
      throw new ConflictException("Campaign launch run cannot be executed from " + run.getStatus());
    }

    run.setStatus(LaunchRunStatus.RUNNING);
    if (run.getStartedAt() == null) run.setStartedAt(Instant.now());
    run.setCompletedAt(null);
    run.setLastErrorCode(null);
    run.setLastErrorMessage(null);
    runRepository.save(run);

    ReachlyoCampaign campaign =
        campaignRepository
            .findByIdAndCompanyId(campaignId, companyId)
            .orElseThrow(() -> new NotFoundException("Reachlyo campaign not found"));
    campaign.setStatus(ReachlyoCampaignStatus.DEPLOYING);
    campaignRepository.save(campaign);

    for (LaunchStep step : steps(companyId, runId)) {
      ProviderDeployment deployment =
          deploymentRepository
              .findByIdAndCompanyId(step.getProviderDeploymentId(), companyId)
              .orElseThrow(() -> new NotFoundException("Provider deployment not found"));
      if (deployment.getDeploymentStatus() == ProviderDeploymentStatus.PENDING
          || deployment.getDeploymentStatus() == ProviderDeploymentStatus.FAILED
          || deployment.getDeploymentStatus() == ProviderDeploymentStatus.PARTIAL_FAILURE) {
        deployment.setDeploymentStatus(ProviderDeploymentStatus.DEPLOYING);
        deployment.setLastErrorCode(null);
        deployment.setLastErrorMessage(null);
        deploymentRepository.save(deployment);
      }
    }
    return run;
  }

  @Transactional
  public LaunchStep beginStep(Long companyId, Long runId, Long stepId) {
    LaunchStep step =
        stepRepository
            .findByIdAndCompanyIdAndLaunchRunIdForUpdate(stepId, companyId, runId)
            .orElseThrow(() -> new NotFoundException("Campaign launch step not found"));

    if (step.getStatus() == LaunchStepStatus.RUNNING) {
      throw new ConflictException("Campaign launch step is already running");
    }
    if (step.getStatus() == LaunchStepStatus.SUCCEEDED
        || step.getStatus() == LaunchStepStatus.SKIPPED) {
      throw new ConflictException("Campaign launch step is already completed");
    }
    if (step.getStatus() != LaunchStepStatus.PENDING
        && step.getStatus() != LaunchStepStatus.FAILED) {
      throw new ConflictException(
          "Campaign launch step cannot be executed from " + step.getStatus());
    }

    step.setStatus(LaunchStepStatus.RUNNING);
    step.setAttemptCount(step.getAttemptCount() + 1);
    step.setLastErrorCode(null);
    step.setLastErrorMessage(null);
    step.setStartedAt(Instant.now());
    step.setCompletedAt(null);
    return stepRepository.save(step);
  }

  @Transactional
  public LaunchStep completeStep(
      Long companyId,
      Long runId,
      Long stepId,
      LaunchStepExecutionResult result) {
    LaunchStep step =
        stepRepository
            .findByIdAndCompanyIdAndLaunchRunId(stepId, companyId, runId)
            .orElseThrow(() -> new NotFoundException("Campaign launch step not found"));
    step.setStatus(result.status());
    step.setLocalResourceId(result.localResourceId());
    step.setExternalResourceId(result.externalResourceId());
    step.setResponseJson(json(result.response()));
    step.setCompletedAt(Instant.now());
    step.setLastErrorCode(null);
    step.setLastErrorMessage(null);
    return stepRepository.save(step);
  }

  @Transactional
  public LaunchStep failStep(
      Long companyId, Long runId, Long stepId, String errorCode, String errorMessage) {
    LaunchStep step =
        stepRepository
            .findByIdAndCompanyIdAndLaunchRunId(stepId, companyId, runId)
            .orElseThrow(() -> new NotFoundException("Campaign launch step not found"));
    step.setStatus(LaunchStepStatus.FAILED);
    step.setLastErrorCode(trimTo(errorCode, 160));
    step.setLastErrorMessage(trimTo(errorMessage, 2000));
    step.setCompletedAt(Instant.now());
    stepRepository.save(step);

    ProviderDeployment deployment =
        deploymentRepository
            .findByIdAndCompanyId(step.getProviderDeploymentId(), companyId)
            .orElseThrow(() -> new NotFoundException("Provider deployment not found"));
    deployment.setDeploymentStatus(
        deployment.getLocalCampaignId() == null
            ? ProviderDeploymentStatus.FAILED
            : ProviderDeploymentStatus.PARTIAL_FAILURE);
    deployment.setLastErrorCode(step.getLastErrorCode());
    deployment.setLastErrorMessage(step.getLastErrorMessage());
    deploymentRepository.save(deployment);
    return step;
  }

  @Transactional
  public LaunchRun failPreflight(
      Long companyId,
      Long campaignId,
      Long runId,
      String errorCode,
      String errorMessage) {
    LaunchRun run = requireRun(companyId, campaignId, runId);
    boolean hasRemoteSuccess =
        steps(companyId, runId).stream()
            .anyMatch(
                step ->
                    (step.getStatus() == LaunchStepStatus.SUCCEEDED
                            || step.getStatus() == LaunchStepStatus.SKIPPED)
                        && step.getLocalResourceId() != null);
    run.setStatus(hasRemoteSuccess ? LaunchRunStatus.PARTIAL_FAILURE : LaunchRunStatus.FAILED);
    run.setLastErrorCode(trimTo(errorCode, 160));
    run.setLastErrorMessage(trimTo(errorMessage, 2000));
    run.setCompletedAt(Instant.now());
    runRepository.save(run);

    ReachlyoCampaign campaign =
        campaignRepository
            .findByIdAndCompanyId(campaignId, companyId)
            .orElseThrow(() -> new NotFoundException("Reachlyo campaign not found"));
    campaign.setStatus(
        hasRemoteSuccess
            ? ReachlyoCampaignStatus.PARTIAL_FAILURE
            : ReachlyoCampaignStatus.FAILED);
    campaignRepository.save(campaign);
    return run;
  }

  @Transactional
  public LaunchRun finishRun(Long companyId, Long campaignId, Long runId) {
    LaunchRun run = requireRun(companyId, campaignId, runId);
    List<LaunchStep> steps = steps(companyId, runId);

    long failedChannels =
        steps.stream()
            .filter(step -> step.getStatus() == LaunchStepStatus.FAILED)
            .map(LaunchStep::getCampaignChannelId)
            .distinct()
            .count();
    long totalChannels =
        steps.stream().map(LaunchStep::getCampaignChannelId).distinct().count();

    ReachlyoCampaign campaign =
        campaignRepository
            .findByIdAndCompanyId(campaignId, companyId)
            .orElseThrow(() -> new NotFoundException("Reachlyo campaign not found"));

    if (failedChannels == 0) {
      run.setStatus(LaunchRunStatus.PAUSED_READY);
      campaign.setStatus(ReachlyoCampaignStatus.PAUSED);
      steps.stream()
          .map(LaunchStep::getProviderDeploymentId)
          .distinct()
          .forEach(
              deploymentId ->
                  deploymentRepository
                      .findByIdAndCompanyId(deploymentId, companyId)
                      .ifPresent(
                          deployment -> {
                            deployment.setDeploymentStatus(ProviderDeploymentStatus.PAUSED);
                            deployment.setLastErrorCode(null);
                            deployment.setLastErrorMessage(null);
                            deploymentRepository.save(deployment);
                          }));
    } else if (failedChannels < totalChannels) {
      run.setStatus(LaunchRunStatus.PARTIAL_FAILURE);
      campaign.setStatus(ReachlyoCampaignStatus.PARTIAL_FAILURE);
    } else {
      run.setStatus(LaunchRunStatus.FAILED);
      campaign.setStatus(ReachlyoCampaignStatus.FAILED);
    }

    run.setCompletedAt(Instant.now());
    runRepository.save(run);
    campaignRepository.save(campaign);
    return run;
  }

  @Transactional(readOnly = true)
  public LaunchRunResponse response(Long companyId, Long campaignId, Long runId) {
    LaunchRun run = requireRun(companyId, campaignId, runId);
    return LaunchRunResponse.from(run, steps(companyId, runId));
  }

  private String json(Object value) {
    if (value == null) return null;
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Unable to serialize launch step response", exception);
    }
  }

  private String trimTo(String value, int max) {
    if (value == null) return null;
    String normalized = value.trim();
    return normalized.length() <= max ? normalized : normalized.substring(0, max);
  }
}
