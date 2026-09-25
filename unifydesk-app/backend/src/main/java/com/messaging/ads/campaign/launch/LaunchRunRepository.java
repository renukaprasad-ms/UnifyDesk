package com.messaging.ads.campaign.launch;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LaunchRunRepository extends JpaRepository<LaunchRun, Long> {

  Optional<LaunchRun> findByIdAndCompanyId(Long id, Long companyId);

  Optional<LaunchRun> findByCompanyIdAndReachlyoCampaignIdAndIdempotencyKey(
      Long companyId, Long reachlyoCampaignId, String idempotencyKey);

  List<LaunchRun> findByCompanyIdAndReachlyoCampaignIdOrderByCreatedAtDesc(
      Long companyId, Long reachlyoCampaignId);
}
