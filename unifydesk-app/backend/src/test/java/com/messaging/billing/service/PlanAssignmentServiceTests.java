package com.messaging.billing.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import com.messaging.billing.dto.PlanAssignmentRequest;
import com.messaging.billing.entity.Plan;
import com.messaging.billing.entity.PlanCompanyAssignment;
import com.messaging.billing.enums.PlanStatus;
import com.messaging.billing.enums.PlanVisibility;
import com.messaging.billing.repository.PlanCompanyAssignmentRepository;
import com.messaging.billing.repository.PlanRepository;
import com.messaging.common.exception.ConflictException;
import com.messaging.company.entity.Company;
import com.messaging.company.repository.CompanyRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PlanAssignmentServiceTests {

  @Mock private PlanRepository planRepository;
  @Mock private CompanyRepository companyRepository;
  @Mock private PlanCompanyAssignmentRepository assignmentRepository;

  @InjectMocks private PlanAssignmentService service;

  private Plan plan;
  private Company company;

  @BeforeEach
  void setUp() {
    plan = new Plan();
    plan.setId(100L);
    plan.setStatus(PlanStatus.PUBLISHED);
    plan.setVisibility(PlanVisibility.PRIVATE);
    company = new Company();
    company.setId(200L);
    when(planRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(plan));
    when(companyRepository.findById(200L)).thenReturn(Optional.of(company));
  }

  @Test
  void currentOverlapIsRejected() {
    Instant from = Instant.parse("2026-10-01T00:00:00Z");
    Instant to = Instant.parse("2026-11-01T00:00:00Z");
    when(assignmentRepository.hasOverlappingActiveWindow(100L, 200L, from, to))
        .thenReturn(true);

    assertThrows(
        ConflictException.class,
        () -> service.assign(100L, 200L, 9L, new PlanAssignmentRequest(from, to, "offer")));

    verify(assignmentRepository, never()).saveAndFlush(any());
  }

  @Test
  void futureOverlapIsRejected() {
    Instant from = Instant.parse("2026-12-01T00:00:00Z");
    Instant to = Instant.parse("2027-01-01T00:00:00Z");
    when(assignmentRepository.hasOverlappingActiveWindow(100L, 200L, from, to))
        .thenReturn(true);

    assertThrows(
        ConflictException.class,
        () -> service.assign(100L, 200L, 9L, new PlanAssignmentRequest(from, to, "future")));
  }

  @Test
  void adjacentWindowIsAcceptedAndPlanRowLockSerializesRaces() {
    Instant from = Instant.parse("2027-01-01T00:00:00Z");
    Instant to = Instant.parse("2027-02-01T00:00:00Z");
    when(assignmentRepository.hasOverlappingActiveWindow(100L, 200L, from, to))
        .thenReturn(false);
    when(assignmentRepository.saveAndFlush(any(PlanCompanyAssignment.class)))
        .thenAnswer(
            invocation -> {
              PlanCompanyAssignment value = invocation.getArgument(0);
              value.setId(300L);
              value.setAssignedAt(Instant.parse("2026-10-03T06:00:00Z"));
              return value;
            });

    var response =
        service.assign(100L, 200L, 9L, new PlanAssignmentRequest(from, to, "adjacent"));

    assertEquals("300", response.assignmentId());
    InOrder order = inOrder(planRepository, assignmentRepository);
    order.verify(planRepository).findByIdForUpdate(100L);
    order.verify(assignmentRepository).hasOverlappingActiveWindow(100L, 200L, from, to);
    order.verify(assignmentRepository).saveAndFlush(any(PlanCompanyAssignment.class));
  }
}
