package com.messaging.ads.campaign.repository;

import com.messaging.ads.campaign.entity.ConversionGoalMapping;
import com.messaging.ads.campaign.enums.CampaignBusinessGoal;
import com.messaging.ads.enums.AdProvider;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversionGoalMappingRepository extends JpaRepository<ConversionGoalMapping, Long> {
  List<ConversionGoalMapping> findByCompanyIdAndReachlyoCampaignIdOrderByProviderAsc(Long companyId, Long campaignId);
  Optional<ConversionGoalMapping> findByIdAndCompanyIdAndReachlyoCampaignId(Long id, Long companyId, Long campaignId);
  Optional<ConversionGoalMapping> findByCompanyIdAndReachlyoCampaignIdAndProviderAndAdAccountIdAndBusinessGoal(
      Long companyId, Long campaignId, AdProvider provider, Long adAccountId, CampaignBusinessGoal businessGoal);
}
