package com.messaging.billing.repository;

import com.messaging.billing.entity.PlanCompanyAssignment;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlanCompanyAssignmentRepository extends JpaRepository<PlanCompanyAssignment, Long> {
  @Query(
      """
      select case when count(a) > 0 then true else false end
      from PlanCompanyAssignment a
      where a.plan.id = :planId
        and a.company.id = :companyId
        and a.revokedAt is null
        and a.effectiveFrom <= :at
        and (a.effectiveTo is null or a.effectiveTo > :at)
      """)
  boolean isActive(
      @Param("planId") Long planId, @Param("companyId") Long companyId, @Param("at") Instant at);

  @Query(
      """
      select a from PlanCompanyAssignment a
      where a.company.id = :companyId
        and a.revokedAt is null
        and a.effectiveFrom <= :at
        and (a.effectiveTo is null or a.effectiveTo > :at)
      order by a.assignedAt desc
      """)
  List<PlanCompanyAssignment> findActiveForCompany(
      @Param("companyId") Long companyId, @Param("at") Instant at);

  List<PlanCompanyAssignment> findByPlanIdOrderByAssignedAtDesc(Long planId);
}
