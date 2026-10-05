package com.messaging.billing.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.messaging.billing.entity.CompanySubscription;
import com.messaging.billing.entity.EntitlementDefinition;
import com.messaging.billing.entity.Plan;
import com.messaging.billing.entity.PlanEntitlement;
import com.messaging.billing.enums.EntitlementCategory;
import com.messaging.billing.enums.BillingCycle;
import com.messaging.billing.enums.EntitlementValueType;
import com.messaging.billing.enums.SubscriptionStatus;
import com.messaging.billing.exception.PlanEntitlementException;
import com.messaging.billing.repository.CompanySubscriptionRepository;
import com.messaging.billing.repository.EntitlementDefinitionRepository;
import com.messaging.billing.repository.PlanEntitlementRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EntitlementServiceTests {

  @Mock private CompanySubscriptionRepository subscriptionRepository;
  @Mock private PlanEntitlementRepository entitlementRepository;
  @Mock private EntitlementDefinitionRepository definitionRepository;

  @InjectMocks private EntitlementService entitlementService;

  @Test
  void missingPlanValueUsesDefinitionDefaultAndFailsClosed() {
    long companyId = 10L;
    Plan plan = plan(20L);
    CompanySubscription subscription = subscription(plan);
    EntitlementDefinition definition = definition("EMAIL_ACCOUNTS", "0");

    when(subscriptionRepository.findFirstByCompanyIdAndStatusOrderByStartedAtDesc(
            companyId, SubscriptionStatus.ACTIVE))
        .thenReturn(Optional.of(subscription));
    when(definitionRepository.findByKey("EMAIL_ACCOUNTS")).thenReturn(Optional.of(definition));
    when(entitlementRepository.findByPlanIdAndKey(plan.getId(), "EMAIL_ACCOUNTS"))
        .thenReturn(Optional.empty());

    assertThrows(
        PlanEntitlementException.class,
        () -> entitlementService.assertWithinLimit(companyId, "EMAIL_ACCOUNTS", 0));
  }

  @Test
  void unlimitedPlanValueDoesNotBlockUsage() {
    long companyId = 10L;
    Plan plan = plan(20L);
    CompanySubscription subscription = subscription(plan);
    EntitlementDefinition definition = definition("EMAIL_ACCOUNTS", "0");
    PlanEntitlement entitlement = new PlanEntitlement();
    entitlement.setPlan(plan);
    entitlement.setKey("EMAIL_ACCOUNTS");
    entitlement.setValue("unlimited");

    when(subscriptionRepository.findFirstByCompanyIdAndStatusOrderByStartedAtDesc(
            companyId, SubscriptionStatus.ACTIVE))
        .thenReturn(Optional.of(subscription));
    when(definitionRepository.findByKey("EMAIL_ACCOUNTS")).thenReturn(Optional.of(definition));
    when(entitlementRepository.findByPlanIdAndKey(plan.getId(), "EMAIL_ACCOUNTS"))
        .thenReturn(Optional.of(entitlement));

    assertDoesNotThrow(
        () -> entitlementService.assertWithinLimit(companyId, "EMAIL_ACCOUNTS", 100_000));
  }

  @Test
  void expiredActivePaidSubscriptionDoesNotGrantEntitlements() {
    long companyId = 10L;
    Instant now = Instant.parse("2026-11-01T00:00:00Z");
    Plan plan = paidPlan(20L);
    CompanySubscription subscription = subscription(plan);
    subscription.setCurrentPeriodEnd(now.minusSeconds(1));

    when(subscriptionRepository.findFirstByCompanyIdAndStatusOrderByStartedAtDesc(
            companyId, SubscriptionStatus.ACTIVE))
        .thenReturn(Optional.of(subscription));

    assertEquals(Optional.empty(), entitlementService.effectiveSubscription(companyId, now));
  }

  @Test
  void activePaidSubscriptionInsideCurrentPeriodRemainsEntitled() {
    long companyId = 10L;
    Instant now = Instant.parse("2026-10-31T23:59:59Z");
    Plan plan = paidPlan(20L);
    CompanySubscription subscription = subscription(plan);
    subscription.setCurrentPeriodEnd(Instant.parse("2026-11-01T00:00:00Z"));

    when(subscriptionRepository.findFirstByCompanyIdAndStatusOrderByStartedAtDesc(
            companyId, SubscriptionStatus.ACTIVE))
        .thenReturn(Optional.of(subscription));

    assertEquals(Optional.of(subscription), entitlementService.effectiveSubscription(companyId, now));
  }

  @Test
  void paidBoundaryEqualityIsEndExclusive() {
    long companyId = 10L;
    Instant boundary = Instant.parse("2026-11-01T00:00:00Z");
    Plan plan = paidPlan(20L);
    CompanySubscription subscription = subscription(plan);
    subscription.setCurrentPeriodEnd(boundary);

    when(subscriptionRepository.findFirstByCompanyIdAndStatusOrderByStartedAtDesc(
            companyId, SubscriptionStatus.ACTIVE))
        .thenReturn(Optional.of(subscription));

    assertEquals(Optional.empty(), entitlementService.effectiveSubscription(companyId, boundary));
  }

  @Test
  void openEndedFreeSubscriptionRemainsEntitled() {
    long companyId = 10L;
    Plan plan = plan(20L);
    plan.setBillingCycle(BillingCycle.FREE);
    plan.setBasePrice(BigDecimal.ZERO);
    CompanySubscription subscription = subscription(plan);
    subscription.setCurrentPeriodEnd(null);

    when(subscriptionRepository.findFirstByCompanyIdAndStatusOrderByStartedAtDesc(
            companyId, SubscriptionStatus.ACTIVE))
        .thenReturn(Optional.of(subscription));

    assertEquals(
        Optional.of(subscription),
        entitlementService.effectiveSubscription(
            companyId, Instant.parse("2026-11-01T00:00:00Z")));
  }

  private Plan paidPlan(Long id) {
    Plan plan = plan(id);
    plan.setBillingCycle(BillingCycle.MONTHLY);
    plan.setBasePrice(new BigDecimal("999.00"));
    return plan;
  }

  private Plan plan(Long id) {
    Plan plan = new Plan();
    plan.setId(id);
    return plan;
  }

  private CompanySubscription subscription(Plan plan) {
    CompanySubscription subscription = new CompanySubscription();
    subscription.setPlan(plan);
    subscription.setStatus(SubscriptionStatus.ACTIVE);
    return subscription;
  }

  private EntitlementDefinition definition(String key, String defaultValue) {
    EntitlementDefinition definition = new EntitlementDefinition();
    definition.setKey(key);
    definition.setName(key);
    definition.setValueType(EntitlementValueType.INTEGER);
    definition.setCategory(EntitlementCategory.CHANNEL);
    definition.setDefaultValue(defaultValue);
    definition.setActive(true);
    return definition;
  }
}
