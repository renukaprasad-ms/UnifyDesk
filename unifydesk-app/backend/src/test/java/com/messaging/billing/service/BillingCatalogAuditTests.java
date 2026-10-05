package com.messaging.billing.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.messaging.billing.bootstrap.SystemEntitlementCatalogInitializer;
import com.messaging.billing.dto.AdminGrantSubscriptionRequest;
import com.messaging.billing.dto.AssignSubscriptionRequest;
import com.messaging.billing.entity.CompanySubscription;
import com.messaging.billing.entity.Plan;
import com.messaging.billing.entity.PlanPrice;
import com.messaging.billing.enums.BillingCycle;
import com.messaging.billing.enums.PaymentPolicy;
import com.messaging.billing.enums.PlanStatus;
import com.messaging.billing.enums.PlanVisibility;
import com.messaging.billing.enums.RateCardStatus;
import com.messaging.billing.enums.SubscriptionStatus;
import com.messaging.billing.enums.SubscriptionTransitionType;
import com.messaging.billing.repository.CompanyRateOverrideRepository;
import com.messaging.billing.repository.CompanySubscriptionRepository;
import com.messaging.billing.repository.EntitlementDefinitionRepository;
import com.messaging.billing.repository.PlanEntitlementRepository;
import com.messaging.billing.repository.PlanPriceRepository;
import com.messaging.billing.repository.PlanRepository;
import com.messaging.billing.repository.RateCardRepository;
import com.messaging.company.entity.Company;
import com.messaging.company.repository.CompanyRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BillingCatalogAuditTests {

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

  @InjectMocks private BillingCatalogService service;

  @Test
  void zeroCostCustomerActivationRetainsAuthenticatedActor() {
    long companyId = 1L;
    long actorId = 77L;
    Company company = company(companyId);
    Plan plan = plan(2L, BigDecimal.ZERO);
    PlanPrice price = price(3L, plan, BigDecimal.ZERO);
    when(companyRepository.findById(companyId)).thenReturn(Optional.of(company));
    when(planRepository.findById(2L)).thenReturn(Optional.of(plan));
    when(planPriceRepository.findById(3L)).thenReturn(Optional.of(price));
    when(subscriptionLifecycleService.currentOptional(companyId)).thenReturn(Optional.empty());
    stubSubscriptionSave();

    service.assignSubscription(
        companyId,
        actorId,
        new AssignSubscriptionRequest(
            "2", "3", SubscriptionStatus.ACTIVE, null, null, null, null));

    verify(subscriptionLifecycleService)
        .recordCreated(
            any(CompanySubscription.class),
            eq(actorId),
            eq("Zero-cost subscription activated"),
            eq(SubscriptionTransitionType.CREATED));
  }

  @Test
  void compedGrantPreservesPolicyActorAndExternalReference() {
    assertManualGrantAudit(PaymentPolicy.COMPED, "comped-2026");
  }

  @Test
  void offlineContractGrantPreservesPolicyActorAndExternalReference() {
    assertManualGrantAudit(PaymentPolicy.OFFLINE_CONTRACT, "contract-42");
  }

  private void assertManualGrantAudit(PaymentPolicy policy, String reference) {
    long companyId = 1L;
    long actorId = 88L;
    Company company = company(companyId);
    Plan plan = plan(2L, new BigDecimal("1000.00"));
    PlanPrice price = price(3L, plan, new BigDecimal("1000.00"));
    when(companyRepository.findById(companyId)).thenReturn(Optional.of(company));
    when(planRepository.findById(2L)).thenReturn(Optional.of(plan));
    when(planPriceRepository.findById(3L)).thenReturn(Optional.of(price));
    when(subscriptionLifecycleService.currentOptional(companyId)).thenReturn(Optional.empty());
    stubSubscriptionSave();

    service.grantSubscription(
        companyId,
        actorId,
        new AdminGrantSubscriptionRequest(
            "2",
            "3",
            SubscriptionStatus.ACTIVE,
            null,
            null,
            null,
            Instant.parse("2026-11-01T00:00:00Z"),
            policy,
            "manual grant",
            reference));

    ArgumentCaptor<CompanySubscription> captor =
        ArgumentCaptor.forClass(CompanySubscription.class);
    verify(subscriptionLifecycleService)
        .recordCreated(
            captor.capture(),
            eq(actorId),
            eq("manual grant"),
            eq(SubscriptionTransitionType.ADMIN_GRANTED));
    assertEquals(policy, captor.getValue().getPaymentPolicy());
    assertEquals(reference, captor.getValue().getExternalContractReference());
  }

  private void stubSubscriptionSave() {
    when(subscriptionRepository.saveAndFlush(any(CompanySubscription.class)))
        .thenAnswer(
            invocation -> {
              CompanySubscription value = invocation.getArgument(0);
              value.setId(10L);
              return value;
            });
  }

  private Company company(Long id) {
    Company company = new Company();
    company.setId(id);
    company.setName("Acme");
    return company;
  }

  private Plan plan(Long id, BigDecimal basePrice) {
    Plan plan = new Plan();
    plan.setId(id);
    plan.setCode("PLAN-" + id);
    plan.setName("Plan " + id);
    plan.setStatus(PlanStatus.PUBLISHED);
    plan.setVisibility(PlanVisibility.PUBLIC);
    plan.setBillingCycle(BillingCycle.MONTHLY);
    plan.setBasePrice(basePrice);
    plan.setCurrency("INR");
    return plan;
  }

  private PlanPrice price(Long id, Plan plan, BigDecimal amount) {
    PlanPrice price = new PlanPrice();
    price.setId(id);
    price.setPlan(plan);
    price.setBillingCycle(BillingCycle.MONTHLY);
    price.setAmount(amount);
    price.setCurrency("INR");
    price.setEffectiveFrom(Instant.parse("2026-01-01T00:00:00Z"));
    price.setStatus(RateCardStatus.ACTIVE);
    return price;
  }
}
