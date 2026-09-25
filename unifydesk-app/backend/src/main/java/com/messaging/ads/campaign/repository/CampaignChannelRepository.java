package com.messaging.ads.campaign.repository;

import com.messaging.ads.campaign.entity.CampaignChannel;
import com.messaging.ads.campaign.enums.CampaignChannelType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CampaignChannelRepository extends JpaRepository<CampaignChannel, Long> {

  List<CampaignChannel> findByCompanyIdAndReachlyoCampaignIdOrderByIdAsc(
      Long companyId, Long reachlyoCampaignId);

  Optional<CampaignChannel> findByIdAndCompanyIdAndReachlyoCampaignId(
      Long id, Long companyId, Long reachlyoCampaignId);

  boolean existsByCompanyIdAndReachlyoCampaignIdAndChannelType(
      Long companyId, Long reachlyoCampaignId, CampaignChannelType channelType);
}
