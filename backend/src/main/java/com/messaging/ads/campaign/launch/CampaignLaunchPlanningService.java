package com.messaging.ads.campaign.launch;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.ads.campaign.compiler.ProviderCampaignPlan;
import com.messaging.ads.campaign.compiler.ProviderPlanIssue;
import com.messaging.ads.campaign.dto.CompileReachlyoCampaignRequest;
import com.messaging.ads.campaign.dto.LaunchChannelRequest;
import com.messaging.ads.campaign.dto.LaunchRunResponse;
import com.messaging.ads.campaign.dto.StartCampaignLaunchRequest;
import com.messaging.ads.campaign.entity.CampaignChannel;
import com.messaging.ads.campaign.entity.ProviderDeployment;
import com.messaging.ads.campaign.entity.ReachlyoCampaign;
import com.messaging.ads.campaign.enums.ProviderDeploymentStatus;
import com.messaging.ads.campaign.enums.ReachlyoCampaignStatus;
import com.messaging.ads.campaign.repository.CampaignChannelRepository;
import com.messaging.ads.campaign.repository.ProviderDeploymentRepository;
import com.messaging.ads.campaign.repository.ReachlyoCampaignRepository;
import com.messaging.ads.campaign.service.CampaignCompilationService;
import com.messaging.common.exception.ConflictException;
import com.messaging.common.exception.NotFoundException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CampaignLaunchPlanningService {

  private final LaunchRunRepository launchRunRepository;
  private final LaunchStepRepository launchStepRepository;
  private final ReachlyoCampaignRepository campaignRepository;
  private final CampaignChannelRepository channelRepository;
  private final ProviderDeploymentRepository deploymentRepository;
  private final CampaignCompilationService compilationService;
  private final LaunchProviderExecutorRegistry executorRegistry;
  private final ObjectMapper objectMapper;

  @Transactional
  public LaunchRunResponse prepare(
      Long companyId,
      Long campaignId,
      Long actorUserId,
      StartCampaignLaunchRequest request) {
    String idempotencyKey = request.idempotencyKey().trim();

    LaunchRun existing =
        launchRunRepository
            .findByCompanyIdAndReachlyoCampaignIdAndIdempotencyKey(
                companyId, campaignId, idempotencyKey)
            .orElse(null);
    if (existing != null) return response(existing);

    ReachlyoCampaign campaign =
        campaignRepository
            .findByIdAndCompanyId(campaignId, companyId)
            .orElseThrow(() -> new NotFoundException("Reachlyo campaign not found"));
    if (campaign.getStatus() == ReachlyoCampaignStatus.ARCHIVED) {
      throw new ConflictException("Archived campaigns cannot be launched");
    }

    LaunchRun run = new LaunchRun();
    run.setCompanyId(companyId);
    run.setReachlyoCampaignId(campaignId);
    run.setIdempotencyKey(idempotencyKey);
    run.setRequestedByUserId(actorUserId);
    run.setStatus(LaunchRunStatus.VALIDATING);
    run = launchRunRepository.save(run);

    campaign.setStatus(ReachlyoCampaignStatus.VALIDATING);
    campaignRepository.save(campaign);

    List<ProviderCampaignPlan> plans = new ArrayList<>();
    List<Map<String, Object>> validationIssues = new ArrayList<>();
    int validationErrorCount = 0;

    for (LaunchChannelRequest channelRequest : request.channels()) {
      try {
        ProviderCampaignPlan plan =
            compilationService.compile(
                companyId,
                campaignId,
                channelRequest.channelId(),
                new CompileReachlyoCampaignRequest(
                    channelRequest.adAccountId(),
                    channelRequest.creativePackId(),
                    channelRequest.audienceDefinitionId()));
        plans.add(plan);

        for (ProviderPlanIssue issue : plan.issues()) {
          validationIssues.add(issueMap(plan, issue));
          if (issue.severity().name().equals("ERROR")) validationErrorCount++;
        }

        LaunchProviderExecutor executor =
            executorRegistry.find(plan.channelType()).orElse(null);
        if (executor == null) {
          validationErrorCount++;
          validationIssues.add(
              Map.of(
                  "channelId", plan.channelId(),
                  "provider", plan.provider().name(),
                  "severity", "ERROR",
                  "code", "LAUNCH_EXECUTOR_NOT_IMPLEMENTED",
                  "field", "channel",
                  "message",
                      "Paused launch execution is not implemented yet for "
                          + plan.channelType()));
        } else {
          for (ProviderPlanIssue issue : executor.preflight(companyId, run, plan)) {
            validationIssues.add(issueMap(plan, issue));
            if (issue.severity().name().equals("ERROR")) validationErrorCount++;
          }
        }
      } catch (RuntimeException exception) {
        validationErrorCount++;
        validationIssues.add(
            Map.of(
                "channelId", channelRequest.channelId().toString(),
                "severity", "ERROR",
                "code", "COMPILE_FAILED",
                "message", safeMessage(exception)));
      }
    }

    run.setPlanSnapshotJson(json(plans));
    run.setValidationIssuesJson(json(validationIssues));

    if (validationErrorCount > 0
        || plans.size() != request.channels().size()
        || plans.stream().anyMatch(plan -> !plan.deployable())) {
      run.setStatus(LaunchRunStatus.VALIDATION_FAILED);
      run.setCompletedAt(Instant.now());
      campaign.setStatus(ReachlyoCampaignStatus.DRAFT);
      campaignRepository.save(campaign);
      launchRunRepository.save(run);
      return response(run);
    }

    int channelOrdinal = 0;
    for (ProviderCampaignPlan plan : plans) {
      Long channelId = Long.valueOf(plan.channelId());
      Long accountId = Long.valueOf(plan.adAccountId());
      CampaignChannel channel =
          channelRepository
              .findByIdAndCompanyIdAndReachlyoCampaignId(channelId, companyId, campaignId)
              .orElseThrow(() -> new NotFoundException("Campaign channel not found"));

      ProviderDeployment deployment =
          deploymentRepository
              .findByCompanyIdAndCampaignChannelIdAndAdAccountId(
                  companyId, channelId, accountId)
              .orElse(null);
      if (deployment != null) {
        throw new ConflictException(
            "A provider deployment already exists for channel "
                + channelId
                + " and account "
                + accountId);
      }

      deployment = new ProviderDeployment();
      deployment.setCompanyId(companyId);
      deployment.setReachlyoCampaignId(campaignId);
      deployment.setCampaignChannelId(channelId);
      deployment.setProvider(channel.getProvider());
      deployment.setAdAccountId(accountId);
      deployment.setDeploymentStatus(ProviderDeploymentStatus.PENDING);
      deployment = deploymentRepository.save(deployment);

      for (var operation : plan.operations()) {
        LaunchStep step = new LaunchStep();
        step.setCompanyId(companyId);
        step.setReachlyoCampaignId(campaignId);
        step.setLaunchRunId(run.getId());
        step.setCampaignChannelId(channelId);
        step.setProviderDeploymentId(deployment.getId());
        step.setAdAccountId(accountId);
        step.setProvider(plan.provider());
        step.setSequence(channelOrdinal * 1000 + operation.sequence());
        step.setOperationKey(operation.operationKey());
        step.setAction(operation.action());
        step.setResource(operation.resource());
        step.setCreatePaused(operation.createPaused());
        step.setStatus(LaunchStepStatus.PENDING);
        step.setStepIdempotencyKey(
            run.getId() + ":" + channelId + ":" + operation.operationKey());
        step.setRequestJson(json(operation.configuration()));
        launchStepRepository.save(step);
      }
      channelOrdinal++;
    }

    run.setStatus(LaunchRunStatus.READY);
    campaign.setStatus(ReachlyoCampaignStatus.READY);
    campaignRepository.save(campaign);
    launchRunRepository.save(run);
    return response(run);
  }

  @Transactional(readOnly = true)
  public LaunchRunResponse get(Long companyId, Long campaignId, Long runId) {
    LaunchRun run =
        launchRunRepository
            .findByIdAndCompanyId(runId, companyId)
            .filter(value -> value.getReachlyoCampaignId().equals(campaignId))
            .orElseThrow(() -> new NotFoundException("Campaign launch run not found"));
    return response(run);
  }

  @Transactional(readOnly = true)
  public List<LaunchRunResponse> list(Long companyId, Long campaignId) {
    campaignRepository
        .findByIdAndCompanyId(campaignId, companyId)
        .orElseThrow(() -> new NotFoundException("Reachlyo campaign not found"));
    return launchRunRepository
        .findByCompanyIdAndReachlyoCampaignIdOrderByCreatedAtDesc(companyId, campaignId)
        .stream()
        .map(this::response)
        .toList();
  }

  private LaunchRunResponse response(LaunchRun run) {
    return LaunchRunResponse.from(
        run,
        launchStepRepository.findByCompanyIdAndLaunchRunIdOrderBySequenceAscIdAsc(
            run.getCompanyId(), run.getId()));
  }

  private Map<String, Object> issueMap(
      ProviderCampaignPlan plan, ProviderPlanIssue issue) {
    Map<String, Object> value = new LinkedHashMap<>();
    value.put("channelId", plan.channelId());
    value.put("provider", plan.provider().name());
    value.put("severity", issue.severity().name());
    value.put("code", issue.code());
    if (issue.field() != null) value.put("field", issue.field());
    value.put("message", issue.message());
    return Map.copyOf(value);
  }

  private String json(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Unable to serialize campaign launch state", exception);
    }
  }

  private String safeMessage(RuntimeException exception) {
    String message = exception.getMessage();
    if (message == null || message.isBlank()) return exception.getClass().getSimpleName();
    return message.length() <= 1000 ? message : message.substring(0, 1000);
  }
}
