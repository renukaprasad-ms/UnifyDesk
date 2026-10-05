package com.messaging.billing.service;

import com.messaging.billing.entity.Plan;
import com.messaging.billing.entity.PlanCompanyAssignment;
import com.messaging.billing.enums.PlanStatus;
import com.messaging.billing.enums.PlanVisibility;
import com.messaging.billing.repository.PlanCompanyAssignmentRepository;
import com.messaging.billing.repository.PlanRepository;
import com.messaging.common.exception.ForbiddenException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PlanEligibilityService {

  private final PlanRepository planRepository;
  private final PlanCompanyAssignmentRepository assignmentRepository;

  @Transactional(readOnly = true)
  public List<Plan> publicPlans() {
    return planRepository.findByStatusAndVisibility(PlanStatus.PUBLISHED, PlanVisibility.PUBLIC);
  }

  @Transactional(readOnly = true)
  public List<Plan> eligiblePlans(Long companyId, Instant at) {
    LinkedHashMap<Long, Plan> eligible = new LinkedHashMap<>();
    publicPlans().forEach(plan -> eligible.put(plan.getId(), plan));
    assignmentRepository.findActiveForCompany(companyId, at).stream()
        .map(PlanCompanyAssignment::getPlan)
        .filter(plan -> plan.getStatus() == PlanStatus.PUBLISHED)
        .filter(plan -> plan.getVisibility() == PlanVisibility.PRIVATE)
        .forEach(plan -> eligible.put(plan.getId(), plan));
    return List.copyOf(eligible.values());
  }

  @Transactional(readOnly = true)
  public void assertCustomerEligible(Long companyId, Plan plan, Instant at) {
    if (plan.getStatus() != PlanStatus.PUBLISHED) {
      throw new ForbiddenException("Plan is not available");
    }
    if (plan.getVisibility() == PlanVisibility.PUBLIC) {
      return;
    }
    if (plan.getVisibility() == PlanVisibility.PRIVATE
        && assignmentRepository.isActive(plan.getId(), companyId, at)) {
      return;
    }
    throw new ForbiddenException("Plan is not available for this company");
  }
}
