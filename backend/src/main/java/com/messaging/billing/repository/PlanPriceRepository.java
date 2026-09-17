package com.messaging.billing.repository;

import com.messaging.billing.entity.PlanPrice;
import com.messaging.billing.enums.BillingCycle;
import com.messaging.billing.enums.PlanPriceStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanPriceRepository extends JpaRepository<PlanPrice, Long> {

  List<PlanPrice> findByPlanIdOrderByEffectiveFromDesc(Long planId);

  List<PlanPrice> findByPlanIdAndBillingCycleAndStatus(
      Long planId, BillingCycle billingCycle, PlanPriceStatus status);

  List<PlanPrice> findByPlanIdAndStatus(Long planId, PlanPriceStatus status);
}
