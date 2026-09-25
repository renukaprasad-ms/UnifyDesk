package com.messaging.ads.campaign.repository;

import com.messaging.ads.campaign.entity.AudienceDefinition;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AudienceDefinitionRepository extends JpaRepository<AudienceDefinition, Long> {
  List<AudienceDefinition> findByCompanyIdAndReachlyoCampaignIdOrderByCreatedAtAsc(Long companyId, Long campaignId);
  Optional<AudienceDefinition> findByIdAndCompanyIdAndReachlyoCampaignId(Long id, Long companyId, Long campaignId);
}
