package com.messaging.ads.campaign.launch;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LaunchStepRepository extends JpaRepository<LaunchStep, Long> {

  List<LaunchStep> findByCompanyIdAndLaunchRunIdOrderBySequenceAscIdAsc(
      Long companyId, Long launchRunId);

  Optional<LaunchStep> findByIdAndCompanyIdAndLaunchRunId(
      Long id, Long companyId, Long launchRunId);
}
