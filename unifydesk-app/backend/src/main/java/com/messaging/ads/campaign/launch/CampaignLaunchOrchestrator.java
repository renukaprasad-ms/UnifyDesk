package com.messaging.ads.campaign.launch;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.ads.campaign.compiler.ProviderCampaignPlan;
import com.messaging.ads.campaign.compiler.ProviderPlanIssue;
import com.messaging.ads.campaign.dto.LaunchRunResponse;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.ConflictException;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CampaignLaunchOrchestrator {

  private final CampaignLaunchStateService stateService;
  private final LaunchProviderExecutorRegistry executorRegistry;
  private final ObjectMapper objectMapper;

  public LaunchRunResponse execute(Long companyId, Long campaignId, Long runId) {
    LaunchRun run = stateService.requireRun(companyId, campaignId, runId);
    if (run.getStatus() == LaunchRunStatus.PAUSED_READY) {
      return stateService.response(companyId, campaignId, runId);
    }

    List<ProviderCampaignPlan> plans = plans(run);
    if (plans.isEmpty()) {
      stateService.failPreflight(
          companyId, campaignId, runId, "PLAN_MISSING", "Campaign launch plan is missing");
      return stateService.response(companyId, campaignId, runId);
    }

    String preflightError = preflight(companyId, run, plans);
    if (preflightError != null) {
      stateService.failPreflight(
          companyId, campaignId, runId, "EXECUTION_PREFLIGHT_FAILED", preflightError);
      return stateService.response(companyId, campaignId, runId);
    }

    stateService.beginRun(companyId, campaignId, runId);
    List<LaunchStep> steps = stateService.steps(companyId, runId);

    for (ProviderCampaignPlan plan : plans) {
      LaunchProviderExecutor executor =
          executorRegistry
              .find(plan.channelType())
              .orElseThrow(
                  () ->
                      new ConflictException(
                          "No launch executor is registered for " + plan.channelType()));

      List<LaunchStep> channelSteps =
          steps.stream()
              .filter(step -> step.getCampaignChannelId().toString().equals(plan.channelId()))
              .sorted(Comparator.comparingInt(LaunchStep::getSequence))
              .toList();

      boolean channelFailed = false;
      for (LaunchStep snapshot : channelSteps) {
        if (snapshot.getStatus() == LaunchStepStatus.SUCCEEDED
            || snapshot.getStatus() == LaunchStepStatus.SKIPPED) {
          continue;
        }
        if (channelFailed) break;

        LaunchStep step =
            stateService.beginStep(companyId, runId, snapshot.getId());
        try {
          LaunchStepExecutionResult result = executor.execute(companyId, run, plan, step);
          if (result.status() != LaunchStepStatus.SUCCEEDED
              && result.status() != LaunchStepStatus.SKIPPED) {
            throw new IllegalStateException(
                "Launch executor returned an invalid terminal step status");
          }
          stateService.completeStep(companyId, runId, step.getId(), result);
        } catch (RuntimeException exception) {
          channelFailed = true;
          stateService.failStep(
              companyId,
              runId,
              step.getId(),
              exception.getClass().getSimpleName(),
              safeMessage(exception));
        }
      }
    }

    stateService.finishRun(companyId, campaignId, runId);
    return stateService.response(companyId, campaignId, runId);
  }

  private String preflight(
      Long companyId, LaunchRun run, List<ProviderCampaignPlan> plans) {
    for (ProviderCampaignPlan plan : plans) {
      LaunchProviderExecutor executor =
          executorRegistry.find(plan.channelType()).orElse(null);
      if (executor == null) {
        return "Launch execution is not implemented for " + plan.channelType();
      }
      List<ProviderPlanIssue> issues = executor.preflight(companyId, run, plan);
      for (ProviderPlanIssue issue : issues) {
        if (issue.severity().name().equals("ERROR")) {
          return plan.channelType() + ": " + issue.message();
        }
      }
    }
    return null;
  }

  private List<ProviderCampaignPlan> plans(LaunchRun run) {
    if (run.getPlanSnapshotJson() == null || run.getPlanSnapshotJson().isBlank()) return List.of();
    try {
      return objectMapper.readValue(
          run.getPlanSnapshotJson(), new TypeReference<List<ProviderCampaignPlan>>() {});
    } catch (Exception exception) {
      throw new BadRequestException("Stored campaign launch plan is invalid");
    }
  }

  private String safeMessage(RuntimeException exception) {
    String message = exception.getMessage();
    if (message == null || message.isBlank()) return exception.getClass().getSimpleName();
    return message.length() <= 2000 ? message : message.substring(0, 2000);
  }
}
