package com.messaging.ads.campaign.repository;

import com.messaging.ads.campaign.entity.AudienceMapping;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AudienceMappingRepository extends JpaRepository<AudienceMapping, Long> {
  List<AudienceMapping> findByCompanyIdAndAudienceDefinitionIdOrderByIdAsc(Long companyId, Long audienceDefinitionId);
  Optional<AudienceMapping> findByCompanyIdAndAudienceDefinitionIdAndCampaignChannelId(Long companyId, Long audienceDefinitionId, Long campaignChannelId);
  void deleteByCompanyIdAndAudienceDefinitionId(Long companyId, Long audienceDefinitionId);
}
