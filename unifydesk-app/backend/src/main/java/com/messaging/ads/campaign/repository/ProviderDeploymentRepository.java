package com.messaging.ads.campaign.repository;

import com.messaging.ads.campaign.entity.ProviderDeployment;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProviderDeploymentRepository extends JpaRepository<ProviderDeployment, Long> {

  List<ProviderDeployment> findByCompanyIdAndReachlyoCampaignIdOrderByIdAsc(
      Long companyId, Long reachlyoCampaignId);

  Optional<ProviderDeployment> findByIdAndCompanyId(Long id, Long companyId);

  Optional<ProviderDeployment> findByCompanyIdAndCampaignChannelIdAndAdAccountId(
      Long companyId, Long campaignChannelId, Long adAccountId);

  boolean existsByCompanyIdAndReachlyoCampaignId(Long companyId, Long reachlyoCampaignId);

  boolean existsByCompanyIdAndCampaignChannelId(Long companyId, Long campaignChannelId);
}
