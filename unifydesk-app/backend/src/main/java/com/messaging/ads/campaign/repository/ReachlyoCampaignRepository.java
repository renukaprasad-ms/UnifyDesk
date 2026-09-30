package com.messaging.ads.campaign.repository;

import com.messaging.ads.campaign.entity.ReachlyoCampaign;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReachlyoCampaignRepository extends JpaRepository<ReachlyoCampaign, Long> {

  Page<ReachlyoCampaign> findByCompanyId(Long companyId, Pageable pageable);

  Optional<ReachlyoCampaign> findByIdAndCompanyId(Long id, Long companyId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      """
      select campaign
        from ReachlyoCampaign campaign
       where campaign.id = :id
         and campaign.companyId = :companyId
      """)
  Optional<ReachlyoCampaign> findByIdAndCompanyIdForUpdate(
      @Param("id") Long id, @Param("companyId") Long companyId);
}
