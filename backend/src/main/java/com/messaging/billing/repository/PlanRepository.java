package com.messaging.billing.repository;

import com.messaging.billing.entity.Plan;
import com.messaging.billing.enums.PlanStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanRepository extends JpaRepository<Plan, Long> {

  List<Plan> findByStatus(PlanStatus status);

  Optional<Plan> findFirstByCodeAndStatusOrderByVersionDesc(String code, PlanStatus status);
}
