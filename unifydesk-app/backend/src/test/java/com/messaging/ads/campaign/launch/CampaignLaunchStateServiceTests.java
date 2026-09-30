package com.messaging.ads.campaign.launch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
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
class CampaignLaunchStateServiceTests {

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
  void pendingStepIsClaimedExactlyOnceUnderWriteLock() {
    LaunchStep pending = step(LaunchStepStatus.PENDING, 0);
    when(stepRepository.findByIdAndCompanyIdAndLaunchRunIdForUpdate(55L, 1L, 100L))
        .thenReturn(Optional.of(pending));
    when(stepRepository.save(pending)).thenReturn(pending);

    LaunchStep claimed = service.beginStep(1L, 100L, 55L);

    assertThat(claimed.getStatus()).isEqualTo(LaunchStepStatus.RUNNING);
    assertThat(claimed.getAttemptCount()).isEqualTo(1);
    assertThat(claimed.getStartedAt()).isNotNull();
    verify(stepRepository).save(pending);
  }

  @Test
  void runningStepCannotBeReacquiredByConcurrentExecutor() {
    LaunchStep running = step(LaunchStepStatus.RUNNING, 1);
    when(stepRepository.findByIdAndCompanyIdAndLaunchRunIdForUpdate(55L, 1L, 100L))
        .thenReturn(Optional.of(running));

    assertThatThrownBy(() -> service.beginStep(1L, 100L, 55L))
        .isInstanceOf(ConflictException.class)
        .hasMessageContaining("already running");
  }

  @Test
  void completedStepCannotRaceFromStaleSnapshotIntoSecondProviderCall() {
    LaunchStep completed = step(LaunchStepStatus.SUCCEEDED, 1);
    when(stepRepository.findByIdAndCompanyIdAndLaunchRunIdForUpdate(55L, 1L, 100L))
        .thenReturn(Optional.of(completed));

    assertThatThrownBy(() -> service.beginStep(1L, 100L, 55L))
        .isInstanceOf(ConflictException.class)
        .hasMessageContaining("already completed");
  }

  @Test
  void failedStepCanBeClaimedForExplicitRetry() {
    LaunchStep failed = step(LaunchStepStatus.FAILED, 1);
    when(stepRepository.findByIdAndCompanyIdAndLaunchRunIdForUpdate(55L, 1L, 100L))
        .thenReturn(Optional.of(failed));
    when(stepRepository.save(failed)).thenReturn(failed);

    LaunchStep claimed = service.beginStep(1L, 100L, 55L);

    assertThat(claimed.getStatus()).isEqualTo(LaunchStepStatus.RUNNING);
    assertThat(claimed.getAttemptCount()).isEqualTo(2);
  }

  private LaunchStep step(LaunchStepStatus status, int attempts) {
    LaunchStep step = new LaunchStep();
    step.setId(55L);
    step.setCompanyId(1L);
    step.setLaunchRunId(100L);
    step.setStatus(status);
    step.setAttemptCount(attempts);
    return step;
  }
}
