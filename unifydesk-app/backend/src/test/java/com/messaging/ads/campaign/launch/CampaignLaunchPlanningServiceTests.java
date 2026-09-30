package com.messaging.ads.campaign.launch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.ads.campaign.compiler.ProviderCampaignPlan;
import com.messaging.ads.campaign.dto.LaunchChannelRequest;
import com.messaging.ads.campaign.dto.StartCampaignLaunchRequest;
import com.messaging.ads.campaign.entity.CampaignChannel;
import com.messaging.ads.campaign.entity.ProviderDeployment;
import com.messaging.ads.campaign.entity.ReachlyoCampaign;
import com.messaging.ads.campaign.enums.BudgetPeriod;
import com.messaging.ads.campaign.enums.CampaignBusinessGoal;
import com.messaging.ads.campaign.enums.CampaignChannelType;
import com.messaging.ads.campaign.enums.ReachlyoCampaignObjective;
import com.messaging.ads.campaign.enums.ReachlyoCampaignStatus;
import com.messaging.ads.campaign.repository.CampaignChannelRepository;
import com.messaging.ads.campaign.repository.ProviderDeploymentRepository;
import com.messaging.ads.campaign.repository.ReachlyoCampaignRepository;
import com.messaging.ads.campaign.service.CampaignCompilationService;
import com.messaging.ads.enums.AdProvider;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CampaignLaunchPlanningServiceTests {

  @Mock private LaunchRunRepository launchRunRepository;
  @Mock private LaunchStepRepository launchStepRepository;
  @Mock private ReachlyoCampaignRepository campaignRepository;
  @Mock private CampaignChannelRepository channelRepository;
  @Mock private ProviderDeploymentRepository deploymentRepository;
  @Mock private CampaignCompilationService compilationService;
  @Mock private LaunchProviderExecutorRegistry executorRegistry;

  private CampaignLaunchPlanningService service;

  @BeforeEach
  void setUp() {
    service =
        new CampaignLaunchPlanningService(
            launchRunRepository,
            launchStepRepository,
            campaignRepository,
            channelRepository,
            deploymentRepository,
            compilationService,
            executorRegistry,
            new ObjectMapper());
  }

  @Test
  void concurrentIdempotencyRetryReloadsExistingRunAfterCampaignLock() {
    ReachlyoCampaign campaign = new ReachlyoCampaign();
    campaign.setId(2L);
    campaign.setCompanyId(1L);
    campaign.setStatus(ReachlyoCampaignStatus.DRAFT);

    LaunchRun existing = new LaunchRun();
    existing.setId(100L);
    existing.setCompanyId(1L);
    existing.setReachlyoCampaignId(2L);
    existing.setIdempotencyKey("launch-race");
    existing.setRequestedByUserId(9L);
    existing.setStatus(LaunchRunStatus.READY);

    when(launchRunRepository.findByCompanyIdAndReachlyoCampaignIdAndIdempotencyKey(
            1L, 2L, "launch-race"))
        .thenReturn(Optional.empty(), Optional.of(existing));
    when(campaignRepository.findByIdAndCompanyIdForUpdate(2L, 1L))
        .thenReturn(Optional.of(campaign));
    when(launchStepRepository.findByCompanyIdAndLaunchRunIdOrderBySequenceAscIdAsc(1L, 100L))
        .thenReturn(List.of());

    var response =
        service.prepare(
            1L,
            2L,
            9L,
            new StartCampaignLaunchRequest(
                "launch-race", List.of(new LaunchChannelRequest(3L, 4L, null, null))));

    assertThat(response.id()).isEqualTo("100");
    verify(launchRunRepository, never()).save(any());
    verify(compilationService, never()).compile(any(), any(), any(), any());
  }

  @Test
  void identicalIdempotencyKeyReturnsExistingRun() {
    LaunchRun existing = new LaunchRun();
    existing.setId(100L);
    existing.setCompanyId(1L);
    existing.setReachlyoCampaignId(2L);
    existing.setIdempotencyKey("launch-1");
    existing.setRequestedByUserId(9L);
    existing.setStatus(LaunchRunStatus.READY);

    when(launchRunRepository.findByCompanyIdAndReachlyoCampaignIdAndIdempotencyKey(
            1L, 2L, "launch-1"))
        .thenReturn(Optional.of(existing));
    when(launchStepRepository.findByCompanyIdAndLaunchRunIdOrderBySequenceAscIdAsc(1L, 100L))
        .thenReturn(List.of());

    var response =
        service.prepare(
            1L,
            2L,
            9L,
            new StartCampaignLaunchRequest(
                "launch-1", List.of(new LaunchChannelRequest(3L, 4L, null, null))));

    assertThat(response.id()).isEqualTo("100");
    assertThat(response.status()).isEqualTo(LaunchRunStatus.READY);
  }
}
