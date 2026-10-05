package com.messaging.billing.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.messaging.billing.entity.Plan;
import com.messaging.billing.enums.PlanStatus;
import com.messaging.billing.enums.PlanVisibility;
import com.messaging.billing.repository.PlanCompanyAssignmentRepository;
import com.messaging.billing.repository.PlanRepository;
import com.messaging.common.exception.ForbiddenException;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PlanEligibilityServiceTests {

  @Mock private PlanRepository planRepository;
  @Mock private PlanCompanyAssignmentRepository assignmentRepository;

  @InjectMocks private PlanEligibilityService service;


  @Test
  void publicDiscoveryAndDirectSelectionUseOnlyLatestPublishedVersionPerCode() {
    Plan freeV1 = new Plan();
    freeV1.setId(101L);
    freeV1.setCode("FREE");
    freeV1.setVersion(1);
    freeV1.setStatus(PlanStatus.PUBLISHED);
    freeV1.setVisibility(PlanVisibility.PUBLIC);

    Plan freeV2 = new Plan();
    freeV2.setId(102L);
    freeV2.setCode("FREE");
    freeV2.setVersion(2);
    freeV2.setStatus(PlanStatus.PUBLISHED);
    freeV2.setVisibility(PlanVisibility.PUBLIC);

    when(planRepository.findByStatusAndVisibility(PlanStatus.PUBLISHED, PlanVisibility.PUBLIC))
        .thenReturn(List.of(freeV1, freeV2));

    assertEquals(List.of(freeV2), service.publicPlans());
    assertThrows(
        ForbiddenException.class,
        () -> service.assertCustomerEligible(10L, freeV1, Instant.parse("2026-10-05T07:00:00Z")));
    service.assertCustomerEligible(10L, freeV2, Instant.parse("2026-10-05T07:00:00Z"));
  }

  @Test
  void privatePlanAssignmentIsTenantScopedAndGuessedIdIsDenied() {
    Plan privatePlan = new Plan();
    privatePlan.setId(77L);
    privatePlan.setStatus(PlanStatus.PUBLISHED);
    privatePlan.setVisibility(PlanVisibility.PRIVATE);
    Instant now = Instant.parse("2026-10-03T06:00:00Z");

    when(assignmentRepository.isActive(77L, 10L, now)).thenReturn(true);
    when(assignmentRepository.isActive(77L, 20L, now)).thenReturn(false);

    service.assertCustomerEligible(10L, privatePlan, now);
    assertThrows(
        ForbiddenException.class,
        () -> service.assertCustomerEligible(20L, privatePlan, now));
  }

  @Test
  void internalPlanIsNeverCustomerSelectableEvenIfIdIsKnown() {
    Plan internal = new Plan();
    internal.setId(88L);
    internal.setStatus(PlanStatus.PUBLISHED);
    internal.setVisibility(PlanVisibility.INTERNAL);

    assertThrows(
        ForbiddenException.class,
        () -> service.assertCustomerEligible(10L, internal, Instant.now()));
  }
}
