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
      value =
          """
          select exists (
            select 1
            from plan_company_assignments a
            where a.plan_id = :planId
              and a.company_id = :companyId
              and a.revoked_at is null
              and a.effective_from <
                  coalesce(cast(:effectiveTo as timestamptz), 'infinity'::timestamptz)
              and coalesce(a.effective_to, 'infinity'::timestamptz) > :effectiveFrom
          )
          """,
      nativeQuery = true)
  boolean hasOverlappingActiveWindow(
      @Param("planId") Long planId,
      @Param("companyId") Long companyId,
      @Param("effectiveFrom") Instant effectiveFrom,
      @Param("effectiveTo") Instant effectiveTo);

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
