package com.messaging.billing.repository;

import com.messaging.billing.entity.PlanEntitlement;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanEntitlementRepository extends JpaRepository<PlanEntitlement, Long> {

  List<PlanEntitlement> findByPlanId(Long planId);

  Optional<PlanEntitlement> findByPlanIdAndKey(Long planId, String key);
}
