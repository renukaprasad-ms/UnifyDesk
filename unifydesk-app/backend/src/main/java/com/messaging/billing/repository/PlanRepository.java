package com.messaging.billing.repository;

import com.messaging.billing.entity.Plan;
import com.messaging.billing.enums.PlanStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanRepository extends JpaRepository<Plan, Long> {

  List<Plan> findByStatus(PlanStatus status);

  Page<Plan> findByNameContainingIgnoreCaseOrCodeContainingIgnoreCase(
      String name, String code, Pageable pageable);

  boolean existsByCodeAndVersion(String code, int version);

  Optional<Plan> findFirstByCodeAndStatusOrderByVersionDesc(String code, PlanStatus status);
}
