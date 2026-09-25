package com.messaging.ads.campaign.repository;

import com.messaging.ads.campaign.entity.BudgetPlan;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BudgetPlanRepository extends JpaRepository<BudgetPlan, Long> {

  Optional<BudgetPlan> findByCompanyIdAndReachlyoCampaignId(
      Long companyId, Long reachlyoCampaignId);
}
