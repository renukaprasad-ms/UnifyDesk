package com.messaging.billing.service;

import com.messaging.billing.dto.PlanAssignmentRequest;
import com.messaging.billing.dto.PlanAssignmentResponse;
import com.messaging.billing.entity.Plan;
import com.messaging.billing.entity.PlanCompanyAssignment;
import com.messaging.billing.enums.PlanStatus;
import com.messaging.billing.enums.PlanVisibility;
import com.messaging.billing.repository.PlanCompanyAssignmentRepository;
import com.messaging.billing.repository.PlanRepository;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.ConflictException;
import com.messaging.common.exception.NotFoundException;
import com.messaging.company.entity.Company;
import com.messaging.company.repository.CompanyRepository;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PlanAssignmentService {

  private final PlanRepository planRepository;
  private final CompanyRepository companyRepository;
  private final PlanCompanyAssignmentRepository assignmentRepository;

  @Transactional
  public PlanAssignmentResponse assign(
      Long planId, Long companyId, Long actorUserId, PlanAssignmentRequest request) {
    Plan plan =
        planRepository.findById(planId).orElseThrow(() -> new NotFoundException("Plan not found"));
    if (plan.getStatus() != PlanStatus.PUBLISHED || plan.getVisibility() != PlanVisibility.PRIVATE) {
      throw new BadRequestException("Only published PRIVATE plans can be assigned to companies");
    }
    Company company =
        companyRepository
            .findById(companyId)
            .orElseThrow(() -> new NotFoundException("Company not found"));
    Instant from =
        request.effectiveFrom() == null ? Instant.now() : request.effectiveFrom();
    if (request.effectiveTo() != null && !request.effectiveTo().isAfter(from)) {
      throw new BadRequestException("Assignment effectiveTo must be after effectiveFrom");
    }
    if (assignmentRepository.isActive(planId, companyId, from)) {
      throw new ConflictException("Company already has an active assignment for this plan");
    }

    PlanCompanyAssignment assignment = new PlanCompanyAssignment();
    assignment.setPlan(plan);
    assignment.setCompany(company);
    assignment.setAssignedByUserId(actorUserId);
    assignment.setEffectiveFrom(from);
    assignment.setEffectiveTo(request.effectiveTo());
    assignment.setReason(normalize(request.reason()));
    return toResponse(assignmentRepository.saveAndFlush(assignment));
  }

  @Transactional
  public PlanAssignmentResponse revoke(Long assignmentId, Long actorUserId, String reason) {
    if (reason == null || reason.isBlank()) {
      throw new BadRequestException("Assignment revocation requires a reason");
    }
    PlanCompanyAssignment assignment =
        assignmentRepository
            .findById(assignmentId)
            .orElseThrow(() -> new NotFoundException("Plan assignment not found"));
    if (assignment.getRevokedAt() != null) {
      return toResponse(assignment);
    }
    assignment.setRevokedAt(Instant.now());
    assignment.setRevokedByUserId(actorUserId);
    assignment.setReason(reason.trim());
    return toResponse(assignmentRepository.saveAndFlush(assignment));
  }

  @Transactional(readOnly = true)
  public List<PlanAssignmentResponse> assignments(Long planId) {
    return assignmentRepository.findByPlanIdOrderByAssignedAtDesc(planId).stream()
        .map(this::toResponse)
        .toList();
  }

  private PlanAssignmentResponse toResponse(PlanCompanyAssignment assignment) {
    return new PlanAssignmentResponse(
        assignment.getId().toString(),
        assignment.getPlan().getId().toString(),
        assignment.getCompany().getId().toString(),
        assignment.getAssignedAt(),
        assignment.getAssignedByUserId().toString(),
        assignment.getEffectiveFrom(),
        assignment.getEffectiveTo(),
        assignment.getRevokedAt(),
        assignment.getRevokedByUserId() == null ? null : assignment.getRevokedByUserId().toString(),
        assignment.getReason());
  }

  private String normalize(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
