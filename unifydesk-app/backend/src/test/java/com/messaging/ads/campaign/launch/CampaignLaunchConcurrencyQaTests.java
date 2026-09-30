package com.messaging.ads.campaign.launch;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.messaging.ads.campaign.repository.ProviderDeploymentRepository;
import com.messaging.ads.campaign.repository.ReachlyoCampaignRepository;
import com.messaging.common.exception.ConflictException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CampaignLaunchConcurrencyQaTests {

  @Mock private LaunchRunRepository runRepository;
  @Mock private LaunchStepRepository stepRepository;
  @Mock private ProviderDeploymentRepository deploymentRepository;
  @Mock private ReachlyoCampaignRepository campaignRepository;

  private CampaignLaunchStateService service;

  @BeforeEach
  void setUp() {
    service =
        new CampaignLaunchStateService(
            runRepository,
            stepRepository,
            deploymentRepository,
            campaignRepository,
            new ObjectMapper());
  }

  @Test
  void runningStepMustNotBeAcquiredBySecondExecutor() {
    LaunchStep running = new LaunchStep();
    running.setId(55L);
    running.setCompanyId(1L);
    running.setLaunchRunId(100L);
    running.setStatus(LaunchStepStatus.RUNNING);
    running.setAttemptCount(1);

    when(stepRepository.findByIdAndCompanyIdAndLaunchRunId(55L, 1L, 100L))
        .thenReturn(Optional.of(running));

    assertThatThrownBy(() -> service.beginStep(1L, 100L, 55L))
        .isInstanceOf(ConflictException.class)
        .hasMessageContaining("already running");
  }
}
