package com.messaging.ads.campaign.repository;

import com.messaging.ads.campaign.entity.CreativePack;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CreativePackRepository extends JpaRepository<CreativePack, Long> {
  List<CreativePack> findByCompanyIdAndReachlyoCampaignIdOrderByCreatedAtAsc(Long companyId, Long campaignId);
  Optional<CreativePack> findByIdAndCompanyIdAndReachlyoCampaignId(Long id, Long companyId, Long campaignId);
}
