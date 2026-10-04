package com.messaging.billing.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.messaging.billing.bootstrap.SystemEntitlementCatalogInitializer;
import com.messaging.billing.dto.AdminGrantSubscriptionRequest;
import com.messaging.billing.dto.AssignSubscriptionRequest;
import com.messaging.billing.dto.CompanySubscriptionResponse;
import com.messaging.billing.dto.CreateCompanyRateOverrideRequest;
import com.messaging.billing.dto.CreatePlanPriceRequest;
import com.messaging.billing.dto.CreateRateCardRequest;
import com.messaging.billing.dto.UpsertEntitlementRequest;
import com.messaging.billing.entity.CompanySubscription;
import com.messaging.billing.entity.Plan;
import com.messaging.billing.entity.PlanPrice;
import com.messaging.billing.enums.BillingCycle;
import com.messaging.billing.enums.BillingCategory;
import com.messaging.billing.enums.CommunicationDirection;
import com.messaging.billing.enums.PaymentPolicy;
import com.messaging.billing.enums.PlanStatus;
import com.messaging.billing.enums.RateCardStatus;
import com.messaging.billing.enums.SubscriptionStatus;
import com.messaging.billing.enums.UsageUnit;
import com.messaging.channel.enums.ChannelType;
import com.messaging.channel.enums.ProviderType;
import com.messaging.billing.repository.CompanyRateOverrideRepository;
import com.messaging.billing.repository.CompanySubscriptionRepository;
import com.messaging.billing.repository.EntitlementDefinitionRepository;
import com.messaging.billing.repository.PlanEntitlementRepository;
import com.messaging.billing.repository.PlanPriceRepository;
import com.messaging.billing.repository.PlanRepository;
import com.messaging.billing.repository.RateCardRepository;
import com.messaging.common.exception.BadRequestException;
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
  @Mock private SubscriptionCheckoutService subscriptionCheckoutService;

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
  void createPlanPriceRejectsNullEffectiveFromAtServiceBoundary() {
    Plan draft = plan(20L, "DRAFT");
    draft.setStatus(PlanStatus.DRAFT);
    when(planRepository.findById(20L)).thenReturn(Optional.of(draft));

    assertThrows(
        BadRequestException.class,
        () ->
            billingCatalogService.createPlanPrice(
                20L,
                new CreatePlanPriceRequest(
                    BillingCycle.MONTHLY, new BigDecimal("999.00"), "INR", null, null)));

    verify(planPriceRepository, never()).saveAndFlush(any(PlanPrice.class));
  }

  @Test
  void createRateCardRejectsNullEffectiveFromAtServiceBoundary() {
    Plan draft = plan(20L, "DRAFT");
    draft.setStatus(PlanStatus.DRAFT);
    when(planRepository.findById(20L)).thenReturn(Optional.of(draft));

    assertThrows(
        BadRequestException.class,
        () ->
            billingCatalogService.createRateCard(
                new CreateRateCardRequest(
                    "20",
                    ChannelType.SMS,
                    ProviderType.MSG91_SMS,
                    "IN",
                    null,
                    BillingCategory.SMS_PROMOTIONAL,
                    CommunicationDirection.OUTBOUND,
                    UsageUnit.MESSAGE,
                    new BigDecimal("0.10"),
                    "INR",
                    null,
                    null)));

    verify(rateCardRepository, never()).saveAndFlush(any());
  }

  @Test
  void createCompanyRateOverrideRejectsNullEffectiveFromAtServiceBoundary() {
    Company company = company(10L);
    when(companyRepository.findById(10L)).thenReturn(Optional.of(company));

    assertThrows(
        BadRequestException.class,
        () ->
            billingCatalogService.createCompanyRateOverride(
                10L,
                77L,
                new CreateCompanyRateOverrideRequest(
                    ChannelType.SMS,
                    ProviderType.MSG91_SMS,
                    BillingCategory.SMS_PROMOTIONAL,
                    CommunicationDirection.OUTBOUND,
                    "IN",
                    null,
                    UsageUnit.MESSAGE,
                    new BigDecimal("0.09"),
                    "INR",
                    null,
                    null,
                    "test")));

    verify(companyRateOverrideRepository, never()).saveAndFlush(any());
  }

  @Test
  void zeroCostSelectionRejectsMalformedFutureExpiredAndNonInrPrices() {
    long companyId = 10L;
    Instant selection = Instant.parse("2026-10-03T06:00:00Z");
    Plan plan = plan(20L, "FREE");
    PlanPrice malformed = freePrice(30L, plan);
    malformed.setEffectiveFrom(null);
    when(planRepository.findById(20L)).thenReturn(Optional.of(plan));
    when(planPriceRepository.findByPlanIdAndStatusOrderByEffectiveFromDesc(
            20L, RateCardStatus.ACTIVE))
        .thenReturn(List.of(malformed));

    assertThrows(
        ConflictException.class,
        () ->
            billingCatalogService.selectFreeSubscription(
                companyId,
                new AssignSubscriptionRequest(
                    "20", null, null, selection, null, null, null)));

    PlanPrice future = freePrice(31L, plan);
    future.setEffectiveFrom(selection.plusSeconds(1));
    PlanPrice expired = freePrice(32L, plan);
    expired.setEffectiveFrom(selection.minusSeconds(3600));
    expired.setEffectiveTo(selection);
    PlanPrice usd = freePrice(33L, plan);
    usd.setEffectiveFrom(selection.minusSeconds(3600));
    usd.setCurrency("USD");
    when(planPriceRepository.findByPlanIdAndStatusOrderByEffectiveFromDesc(
            20L, RateCardStatus.ACTIVE))
        .thenReturn(List.of(future, expired, usd));

    assertThrows(
        BadRequestException.class,
        () ->
            billingCatalogService.selectFreeSubscription(
                companyId,
                new AssignSubscriptionRequest(
                    "20", null, null, selection, null, null, null)));
  }

  @Test
  void providerConfirmedPaidActivationDelegatesOnlyToCanonicalCheckoutContract() {
    Instant confirmedAt = Instant.parse("2026-10-03T06:00:00Z");
    CompanySubscriptionResponse expected =
        new CompanySubscriptionResponse(
            "40",
            "10",
            "20",
            "30",
            "GROWTH",
            "Growth",
            SubscriptionStatus.ACTIVE,
            confirmedAt,
            null,
            confirmedAt,
            Instant.parse("2026-11-03T06:00:00Z"),
            confirmedAt,
            null,
            null,
            false,
            null,
            null,
            PaymentPolicy.CASHFREE,
            null);
    when(subscriptionCheckoutService.activateConfirmedInitialCheckout(
            10L, 60L, 50L, confirmedAt))
        .thenReturn(expected);

    CompanySubscriptionResponse response =
        billingCatalogService.activateProviderConfirmedSubscription(
            10L, 60L, 50L, confirmedAt);

    assertEquals(expected, response);
    verify(subscriptionCheckoutService)
        .activateConfirmedInitialCheckout(10L, 60L, 50L, confirmedAt);
    verify(subscriptionRepository, never()).saveAndFlush(any(CompanySubscription.class));
  }

  @Test
  void expiredManualGrantPlanPriceIsRejected() {
    Instant start = Instant.parse("2026-10-03T06:00:00Z");
    Company company = company(10L);
    Plan plan = paidPlan(20L, "GROWTH");
    PlanPrice price = paidPrice(30L, plan, "999.00");
    price.setEffectiveTo(start);

    when(companyRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(company));
    when(planRepository.findById(20L)).thenReturn(Optional.of(plan));
    when(planPriceRepository.findById(30L)).thenReturn(Optional.of(price));

    assertThrows(
        BadRequestException.class,
        () ->
            billingCatalogService.grantSubscription(
                10L, 77L, grantRequest(plan, price, PaymentPolicy.COMPED, start, null)));
  }

  @Test
  void futureManualGrantPlanPriceIsRejected() {
    Instant start = Instant.parse("2026-10-03T06:00:00Z");
    Company company = company(10L);
    Plan plan = paidPlan(20L, "GROWTH");
    PlanPrice price = paidPrice(30L, plan, "999.00");
    price.setEffectiveFrom(start.plusSeconds(1));

    when(companyRepository.findById(10L)).thenReturn(Optional.of(company));
    when(planRepository.findById(20L)).thenReturn(Optional.of(plan));
    when(planPriceRepository.findById(30L)).thenReturn(Optional.of(price));

    assertThrows(
        BadRequestException.class,
        () ->
            billingCatalogService.grantSubscription(
                10L, 77L, grantRequest(plan, price, PaymentPolicy.COMPED, start, null)));
  }

  @Test
  void nonInrManualGrantPlanPriceIsRejected() {
    Instant start = Instant.parse("2026-10-03T06:00:00Z");
    Company company = company(10L);
    Plan plan = paidPlan(20L, "GROWTH");
    PlanPrice price = paidPrice(30L, plan, "999.00");
    price.setCurrency("USD");

    when(companyRepository.findById(10L)).thenReturn(Optional.of(company));
    when(planRepository.findById(20L)).thenReturn(Optional.of(plan));
    when(planPriceRepository.findById(30L)).thenReturn(Optional.of(price));

    assertThrows(
        BadRequestException.class,
        () ->
            billingCatalogService.grantSubscription(
                10L, 77L, grantRequest(plan, price, PaymentPolicy.COMPED, start, null)));
  }

  @Test
  void wrongPlanManualGrantPriceIsRejected() {
    Instant start = Instant.parse("2026-10-03T06:00:00Z");
    Company company = company(10L);
    Plan plan = paidPlan(20L, "GROWTH");
    Plan other = paidPlan(21L, "OTHER");
    PlanPrice price = paidPrice(30L, other, "999.00");

    when(companyRepository.findById(10L)).thenReturn(Optional.of(company));
    when(planRepository.findById(20L)).thenReturn(Optional.of(plan));
    when(planPriceRepository.findById(30L)).thenReturn(Optional.of(price));

    assertThrows(
        BadRequestException.class,
        () ->
            billingCatalogService.grantSubscription(
                10L, 77L, grantRequest(plan, price, PaymentPolicy.COMPED, start, null)));
  }

  @Test
  void compedGrantKeepsRealCommercialPriceAndDerivesPeriod() {
    Instant start = Instant.parse("2026-10-03T06:00:00Z");
    Company company = company(10L);
    Plan plan = paidPlan(20L, "GROWTH");
    PlanPrice price = paidPrice(30L, plan, "999.00");

    when(companyRepository.findById(10L)).thenReturn(Optional.of(company));
    when(planRepository.findById(20L)).thenReturn(Optional.of(plan));
    when(planPriceRepository.findById(30L)).thenReturn(Optional.of(price));
    when(subscriptionLifecycleService.currentOptional(10L)).thenReturn(Optional.empty());
    when(subscriptionRepository.saveAndFlush(any(CompanySubscription.class)))
        .thenAnswer(
            invocation -> {
              CompanySubscription value = invocation.getArgument(0);
              value.setId(40L);
              return value;
            });

    CompanySubscriptionResponse response =
        billingCatalogService.grantSubscription(
            10L, 77L, grantRequest(plan, price, PaymentPolicy.COMPED, start, null));

    assertEquals("30", response.planPriceId());
    assertEquals(PaymentPolicy.COMPED, response.paymentPolicy());
    assertEquals(start, response.currentPeriodStart());
    assertEquals(Instant.parse("2026-11-03T06:00:00Z"), response.currentPeriodEnd());
    verify(subscriptionCheckoutService, never())
        .activateConfirmedInitialCheckout(any(), any(), any(), any());
  }

  @Test
  void offlineContractKeepsRealCommercialPriceAndExplicitBoundedPeriod() {
    Instant start = Instant.parse("2026-10-03T06:00:00Z");
    Instant contractEnd = Instant.parse("2027-01-03T06:00:00Z");
    Company company = company(10L);
    Plan plan = paidPlan(20L, "GROWTH");
    PlanPrice price = paidPrice(30L, plan, "999.00");

    when(companyRepository.findById(10L)).thenReturn(Optional.of(company));
    when(planRepository.findById(20L)).thenReturn(Optional.of(plan));
    when(planPriceRepository.findById(30L)).thenReturn(Optional.of(price));
    when(subscriptionLifecycleService.currentOptional(10L)).thenReturn(Optional.empty());
    when(subscriptionRepository.saveAndFlush(any(CompanySubscription.class)))
        .thenAnswer(
            invocation -> {
              CompanySubscription value = invocation.getArgument(0);
              value.setId(41L);
              return value;
            });

    CompanySubscriptionResponse response =
        billingCatalogService.grantSubscription(
            10L,
            77L,
            grantRequest(plan, price, PaymentPolicy.OFFLINE_CONTRACT, start, contractEnd));

    assertEquals("30", response.planPriceId());
    assertEquals(PaymentPolicy.OFFLINE_CONTRACT, response.paymentPolicy());
    assertEquals(contractEnd, response.currentPeriodEnd());
    verify(subscriptionCheckoutService, never())
        .activateConfirmedInitialCheckout(any(), any(), any(), any());
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

  private Plan paidPlan(Long id, String code) {
    Plan plan = plan(id, code);
    plan.setBillingCycle(BillingCycle.MONTHLY);
    plan.setBasePrice(new BigDecimal("999.00"));
    return plan;
  }

  private PlanPrice paidPrice(Long id, Plan plan, String amount) {
    PlanPrice price = new PlanPrice();
    price.setId(id);
    price.setPlan(plan);
    price.setBillingCycle(BillingCycle.MONTHLY);
    price.setAmount(new BigDecimal(amount));
    price.setCurrency("INR");
    price.setEffectiveFrom(Instant.parse("2026-01-01T00:00:00Z"));
    price.setStatus(RateCardStatus.ACTIVE);
    return price;
  }

  private AdminGrantSubscriptionRequest grantRequest(
      Plan plan,
      PlanPrice price,
      PaymentPolicy policy,
      Instant start,
      Instant periodEnd) {
    return new AdminGrantSubscriptionRequest(
        plan.getId().toString(),
        price.getId().toString(),
        SubscriptionStatus.ACTIVE,
        start,
        null,
        null,
        periodEnd,
        policy,
        "Commercial grant",
        policy == PaymentPolicy.OFFLINE_CONTRACT ? "contract-123" : null);
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
