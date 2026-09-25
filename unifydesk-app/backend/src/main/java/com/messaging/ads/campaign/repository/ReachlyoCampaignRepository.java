package com.messaging.ads.campaign.repository;

import com.messaging.ads.campaign.entity.ReachlyoCampaign;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReachlyoCampaignRepository extends JpaRepository<ReachlyoCampaign, Long> {

  Page<ReachlyoCampaign> findByCompanyId(Long companyId, Pageable pageable);

  Optional<ReachlyoCampaign> findByIdAndCompanyId(Long id, Long companyId);
}
