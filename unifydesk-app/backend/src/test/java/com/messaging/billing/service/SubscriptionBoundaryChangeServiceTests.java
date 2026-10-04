package com.messaging.billing.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.messaging.billing.entity.CompanySubscription;
import com.messaging.billing.entity.Plan;
import com.messaging.billing.entity.PlanPrice;
import com.messaging.billing.entity.SubscriptionChange;
import com.messaging.billing.enums.BillingCycle;
import com.messaging.billing.enums.RateCardStatus;
import com.messaging.billing.enums.SubscriptionChangeStatus;
import com.messaging.billing.repository.SubscriptionChangeRepository;
import com.messaging.common.exception.ConflictException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SubscriptionBoundaryChangeServiceTests {

  @Mock private SubscriptionChangeRepository repository;
  @InjectMocks private SubscriptionBoundaryChangeService service;

  @Test
  void exactScheduledBoundaryResolvesCanonicalTarget() {
    Instant start = Instant.parse("2026-10-01T00:00:00Z");
    Instant boundary = Instant.parse("2026-11-01T00:00:00Z");
    CompanySubscription subscription = subscription(start, boundary);
    SubscriptionChange change = change(subscription, start, boundary);

    when(repository.findScheduledDowngradesUpToBoundaryForUpdate(40L, boundary))
        .thenReturn(List.of(change));

    assertEquals(
        change,
        service.resolveScheduledDowngradeForBoundary(subscription, boundary).orElseThrow());
  }

  @Test
  void staleDowngradeAfterSubscriptionBoundaryAdvancedFailsClosed() {
    Instant originalStart = Instant.parse("2026-10-01T00:00:00Z");
    Instant staleBoundary = Instant.parse("2026-11-01T00:00:00Z");
    CompanySubscription subscription =
        subscription(staleBoundary, Instant.parse("2026-12-01T00:00:00Z"));
    SubscriptionChange stale = change(subscription, originalStart, staleBoundary);

    when(repository.findScheduledDowngradesUpToBoundaryForUpdate(
            40L, subscription.getCurrentPeriodEnd()))
        .thenReturn(List.of(stale));

    assertThrows(
        ConflictException.class,
        () ->
            service.resolveScheduledDowngradeForBoundary(
                subscription, subscription.getCurrentPeriodEnd()));
  }

  private CompanySubscription subscription(Instant start, Instant end) {
    Plan current = plan(20L);
    PlanPrice price = price(21L, current);
    CompanySubscription subscription = new CompanySubscription();
    subscription.setId(40L);
    subscription.setPlan(current);
    subscription.setPlanPrice(price);
    subscription.setCurrentPeriodStart(start);
    subscription.setCurrentPeriodEnd(end);
    return subscription;
  }

  private SubscriptionChange change(
      CompanySubscription subscription, Instant start, Instant boundary) {
    Plan target = plan(30L);
    SubscriptionChange change = new SubscriptionChange();
    change.setId(50L);
    change.setSubscription(subscription);
    change.setFromPlan(subscription.getPlan());
    change.setFromPlanPrice(subscription.getPlanPrice());
    change.setToPlan(target);
    change.setToPlanPrice(price(31L, target));
    change.setCurrentPeriodStart(start);
    change.setCurrentPeriodEnd(boundary);
    change.setEffectiveAt(boundary);
    change.setStatus(SubscriptionChangeStatus.SCHEDULED);
    return change;
  }

  private Plan plan(Long id) {
    Plan plan = new Plan();
    plan.setId(id);
    return plan;
  }

  private PlanPrice price(Long id, Plan plan) {
    PlanPrice price = new PlanPrice();
    price.setId(id);
    price.setPlan(plan);
    price.setBillingCycle(BillingCycle.MONTHLY);
    price.setAmount(new BigDecimal("100.00"));
    price.setCurrency("INR");
    price.setStatus(RateCardStatus.ACTIVE);
    price.setEffectiveFrom(Instant.parse("2026-01-01T00:00:00Z"));
    return price;
  }
}
