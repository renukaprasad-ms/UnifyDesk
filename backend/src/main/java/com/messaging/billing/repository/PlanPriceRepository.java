package com.messaging.billing.repository;

import com.messaging.billing.entity.PlanPrice;
import com.messaging.billing.enums.RateCardStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanPriceRepository extends JpaRepository<PlanPrice, Long> {
  List<PlanPrice> findByPlanIdAndStatusOrderByEffectiveFromDesc(Long planId, RateCardStatus status);

  List<PlanPrice> findByPlanIdOrderByEffectiveFromDesc(Long planId);
}
