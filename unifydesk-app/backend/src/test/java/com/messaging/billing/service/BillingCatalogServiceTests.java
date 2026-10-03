package com.messaging.billing.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.messaging.billing.bootstrap.SystemEntitlementCatalogInitializer;
import com.messaging.billing.dto.AssignSubscriptionRequest;
import com.messaging.billing.dto.CompanySubscriptionResponse;
import com.messaging.billing.dto.UpsertEntitlementRequest;
import com.messaging.billing.entity.CompanySubscription;
import com.messaging.billing.entity.Plan;
import com.messaging.billing.entity.PlanPrice;
import com.messaging.billing.enums.BillingCycle;
import com.messaging.billing.enums.PaymentPolicy;
import com.messaging.billing.enums.PlanStatus;
import com.messaging.billing.enums.RateCardStatus;
import com.messaging.billing.enums.SubscriptionStatus;
import com.messaging.billing.repository.CompanyRateOverrideRepository;
import com.messaging.billing.repository.CompanySubscriptionRepository;
import com.messaging.billing.repository.EntitlementDefinitionRepository;
import com.messaging.billing.repository.PlanEntitlementRepository;
import com.messaging.billing.repository.PlanPriceRepository;
import com.messaging.billing.repository.PlanRepository;
import com.messaging.billing.repository.RateCardRepository;
import com.messaging.common.exception.ConflictException;
import com.messaging.company.entity.Company;
import com.messaging.company.repository.CompanyRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BillingCatalogServiceTests {

  @Mock private PlanRepository planRepository;
  @Mock private PlanPriceRepository planPriceRepository;
  @Mock private EntitlementDefinitionRepository entitlementDefinitionRepository;
  @Mock private PlanEntitlementRepository entitlementRepository;
  @Mock private RateCardRepository rateCardRepository;
  @Mock private CompanyRateOverrideRepository companyRateOverrideRepository;
  @Mock private CompanySubscriptionRepository subscriptionRepository;
  @Mock private CompanyRepository companyRepository;
  @Mock private SystemEntitlementCatalogInitializer entitlementCatalogInitializer;
  @Mock private PlanEligibilityService planEligibilityService;
  @Mock private SubscriptionLifecycleService subscriptionLifecycleService;

  @InjectMocks private BillingCatalogService billingCatalogService;

  @Test
  void selectingAlreadyActiveFreePlanIsIdempotent() {
    long companyId = 10L;
    Plan plan = plan(20L, "FREE");
    PlanPrice price = freePrice(30L, plan);
    Company company = company(companyId);
    CompanySubscription current =
        subscription(40L, company, plan, price, SubscriptionStatus.ACTIVE);

    when(planRepository.findById(plan.getId())).thenReturn(Optional.of(plan));
    when(planPriceRepository.findByPlanIdAndStatusOrderByEffectiveFromDesc(
            plan.getId(), RateCardStatus.ACTIVE))
        .thenReturn(List.of(price));
    when(subscriptionRepository.findFirstByCompanyIdAndStatusOrderByStartedAtDesc(
            companyId, SubscriptionStatus.ACTIVE))
        .thenReturn(Optional.of(current));

    CompanySubscriptionResponse response =
        billingCatalogService.selectFreeSubscription(
            companyId,
            new AssignSubscriptionRequest(
                plan.getId().toString(), null, null, null, null, null, null));

    assertEquals(current.getId().toString(), response.subscriptionId());
    assertEquals(plan.getId().toString(), response.planId());
    verify(subscriptionRepository, never()).saveAndFlush(any(CompanySubscription.class));
  }

  @Test
  void providerConfirmedPaidActivationFreezesCashfreeSubscriptionContract() {
    long companyId = 10L;
    Instant confirmedAt = Instant.parse("2026-10-03T06:00:00Z");
    Instant periodEnd = Instant.parse("2026-11-03T06:00:00Z");
    Company company = company(companyId);
    Plan plan = plan(20L, "GROWTH");
    plan.setBillingCycle(BillingCycle.MONTHLY);
    plan.setBasePrice(new BigDecimal("999.00"));

    PlanPrice price = new PlanPrice();
    price.setId(30L);
    price.setPlan(plan);
    price.setBillingCycle(BillingCycle.MONTHLY);
    price.setAmount(new BigDecimal("999.00"));
    price.setCurrency("INR");
    price.setEffectiveFrom(confirmedAt.minusSeconds(60));
    price.setStatus(RateCardStatus.ACTIVE);

    when(companyRepository.findByIdForUpdate(companyId)).thenReturn(Optional.of(company));
    when(subscriptionLifecycleService.currentOptional(companyId)).thenReturn(Optional.empty());
    when(planRepository.findById(plan.getId())).thenReturn(Optional.of(plan));
    when(planPriceRepository.findById(price.getId())).thenReturn(Optional.of(price));
    when(subscriptionRepository.saveAndFlush(any(CompanySubscription.class)))
        .thenAnswer(
            invocation -> {
              CompanySubscription saved = invocation.getArgument(0);
              saved.setId(40L);
              return saved;
            });

    CompanySubscriptionResponse response =
        billingCatalogService.activateProviderConfirmedSubscription(
            companyId,
            77L,
            new AssignSubscriptionRequest(
                plan.getId().toString(),
                price.getId().toString(),
                SubscriptionStatus.ACTIVE,
                confirmedAt,
                null,
                null,
                periodEnd),
            confirmedAt);

    assertEquals("40", response.subscriptionId());
    assertEquals(PaymentPolicy.CASHFREE, response.paymentPolicy());
    assertEquals(confirmedAt, response.currentPeriodStart());
    assertEquals(periodEnd, response.currentPeriodEnd());
    verify(subscriptionLifecycleService)
        .recordCreated(
            any(CompanySubscription.class),
            org.mockito.ArgumentMatchers.eq(77L),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.any());
  }

  @Test
  void publishedPlanEntitlementsAreImmutable() {
    Plan plan = plan(20L, "FREE");
    when(planRepository.findById(plan.getId())).thenReturn(Optional.of(plan));

    assertThrows(
        ConflictException.class,
        () ->
            billingCatalogService.upsertEntitlement(
                plan.getId(), new UpsertEntitlementRequest("EMAIL_ACCOUNTS", "3")));
  }

  @Test
  void retiredPlanEntitlementsCannotBeChanged() {
    Plan plan = plan(20L, "FREE");
    plan.setStatus(PlanStatus.RETIRED);
    when(planRepository.findById(plan.getId())).thenReturn(Optional.of(plan));

    assertThrows(
        ConflictException.class,
        () ->
            billingCatalogService.upsertEntitlement(
                plan.getId(), new UpsertEntitlementRequest("EMAIL_ACCOUNTS", "3")));
  }

  @Test
  void directPlanReplacementIsRejectedInFavorOfChangeContract() {
    long companyId = 10L;
    Company company = company(companyId);
    Plan oldPlan = plan(20L, "FREE");
    Plan targetPlan = plan(21L, "STARTER");
    CompanySubscription current =
        subscription(40L, company, oldPlan, null, SubscriptionStatus.ACTIVE);

    when(companyRepository.findById(companyId)).thenReturn(Optional.of(company));
    when(planRepository.findById(targetPlan.getId())).thenReturn(Optional.of(targetPlan));
    when(subscriptionLifecycleService.currentOptional(companyId)).thenReturn(Optional.of(current));

    assertThrows(
        ConflictException.class,
        () ->
            billingCatalogService.assignSubscription(
                companyId,
                new AssignSubscriptionRequest(
                    targetPlan.getId().toString(), null, null, null, null, null, null)));

    verify(subscriptionRepository, never()).saveAndFlush(any(CompanySubscription.class));
  }

  private Plan plan(Long id, String code) {
    Plan plan = new Plan();
    plan.setId(id);
    plan.setCode(code);
    plan.setName(code);
    plan.setStatus(PlanStatus.PUBLISHED);
    plan.setBillingCycle(BillingCycle.FREE);
    plan.setBasePrice(BigDecimal.ZERO);
    plan.setCurrency("INR");
    plan.setVersion(1);
    return plan;
  }

  private PlanPrice freePrice(Long id, Plan plan) {
    PlanPrice price = new PlanPrice();
    price.setId(id);
    price.setPlan(plan);
    price.setBillingCycle(BillingCycle.FREE);
    price.setAmount(BigDecimal.ZERO);
    price.setCurrency("INR");
    price.setEffectiveFrom(Instant.now());
    price.setStatus(RateCardStatus.ACTIVE);
    return price;
  }

  private Company company(Long id) {
    Company company = new Company();
    company.setId(id);
    return company;
  }

  private CompanySubscription subscription(
      Long id, Company company, Plan plan, PlanPrice planPrice, SubscriptionStatus status) {
    CompanySubscription subscription = new CompanySubscription();
    subscription.setId(id);
    subscription.setCompany(company);
    subscription.setPlan(plan);
    subscription.setPlanPrice(planPrice);
    subscription.setStatus(status);
    subscription.setStartedAt(Instant.now());
    subscription.setCurrentPeriodStart(subscription.getStartedAt());
    return subscription;
  }
}
