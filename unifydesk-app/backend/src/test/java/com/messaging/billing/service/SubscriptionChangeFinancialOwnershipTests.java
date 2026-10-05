package com.messaging.billing.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.messaging.billing.entity.BillingPeriod;
import com.messaging.billing.entity.CompanySubscription;
import com.messaging.billing.entity.Plan;
import com.messaging.billing.entity.PlanPrice;
import com.messaging.billing.entity.SubscriptionChange;
import com.messaging.billing.enums.BillingCycle;
import com.messaging.billing.enums.BillingPeriodKind;
import com.messaging.billing.enums.PaymentPolicy;
import com.messaging.billing.enums.PlanChangeDirection;
import com.messaging.billing.enums.PlanStatus;
import com.messaging.billing.enums.PlanVisibility;
import com.messaging.billing.enums.RateCardStatus;
import com.messaging.billing.enums.SubscriptionStatus;
import com.messaging.billing.repository.BillingPeriodRepository;
import com.messaging.billing.repository.PlanPriceRepository;
import com.messaging.billing.repository.PlanRepository;
import com.messaging.billing.repository.SubscriptionChangeRepository;
import com.messaging.common.exception.ConflictException;
import com.messaging.company.entity.Company;
import com.messaging.company.repository.CompanyRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SubscriptionChangeFinancialOwnershipTests {

  private SubscriptionChangeRepository changes;
  private PlanRepository plans;
  private PlanPriceRepository prices;
  private CompanyRepository companies;
  private PlanEligibilityService eligibility;
  private SubscriptionLifecycleService lifecycle;
  private UpgradeProrationCalculator proration;
  private PlanChangeClassifier classifier;
  private BillingPeriodService periods;
  private BillingPeriodRepository periodRepository;
  private UpgradeInvoiceService upgradeInvoices;
  private SubscriptionChangeService service;

  private Company company;
  private Plan currentPlan;
  private Plan targetPlan;
  private PlanPrice currentPrice;
  private PlanPrice targetPrice;
  private CompanySubscription subscription;
  private final Instant boundary = Instant.parse("2026-11-01T00:00:00Z");

  @BeforeEach
  void setUp() {
    changes = mock(SubscriptionChangeRepository.class);
    plans = mock(PlanRepository.class);
    prices = mock(PlanPriceRepository.class);
    companies = mock(CompanyRepository.class);
    eligibility = mock(PlanEligibilityService.class);
    lifecycle = mock(SubscriptionLifecycleService.class);
    proration = mock(UpgradeProrationCalculator.class);
    classifier = mock(PlanChangeClassifier.class);
    periods = mock(BillingPeriodService.class);
    periodRepository = mock(BillingPeriodRepository.class);
    upgradeInvoices = mock(UpgradeInvoiceService.class);
    service = new SubscriptionChangeService(
        changes, plans, prices, companies, eligibility, lifecycle, proration, classifier,
        periods, periodRepository, upgradeInvoices);

    company = new Company();
    company.setId(10L);
    currentPlan = plan(20L);
    targetPlan = plan(30L);
    currentPrice = price(21L, currentPlan, "1000.00", Instant.parse("2026-01-01T00:00:00Z"));
    targetPrice = price(31L, targetPlan, "500.00", boundary);
    subscription = new CompanySubscription();
    subscription.setId(40L);
    subscription.setCompany(company);
    subscription.setPlan(currentPlan);
    subscription.setPlanPrice(currentPrice);
    subscription.setStatus(SubscriptionStatus.ACTIVE);
    subscription.setPaymentPolicy(PaymentPolicy.CASHFREE);
    subscription.setCurrentPeriodStart(Instant.parse("2026-10-01T00:00:00Z"));
    subscription.setCurrentPeriodEnd(boundary);
  }

  @Test
  void renewalPeriodCreatedFirstMovesLaterDowngradeToNextBoundary() {
    Instant requestedAt = Instant.parse("2026-10-20T00:00:00Z");
    Instant nextBoundary = Instant.parse("2026-12-01T00:00:00Z");
    BillingPeriod owned = ownedRenewal(nextBoundary);
    targetPrice.setEffectiveFrom(nextBoundary.minusSeconds(1));
    stubNewDowngrade("late-down");
    when(periodRepository.findByCompanyIdAndKindAndStartAt(10L, BillingPeriodKind.PLATFORM, boundary))
        .thenReturn(List.of(owned));
    when(classifier.classify(currentPlan, currentPrice, targetPlan, targetPrice))
        .thenReturn(PlanChangeDirection.DOWNGRADE);

    var result = service.scheduleDowngrade(10L, 30L, 31L, "late-down", 99L, requestedAt);

    assertEquals(nextBoundary, result.effectiveAt());
    assertEquals(boundary, capturedChange().getCurrentPeriodStart());
    assertEquals(nextBoundary, capturedChange().getCurrentPeriodEnd());
  }

  @Test
  void exactBoundaryRequestCannotCreateStaleSameBoundaryDowngrade() {
    stubNewDowngrade("at-boundary");
    when(periodRepository.findByCompanyIdAndKindAndStartAt(10L, BillingPeriodKind.PLATFORM, boundary))
        .thenReturn(List.of());

    assertThrows(
        ConflictException.class,
        () -> service.scheduleDowngrade(10L, 30L, 31L, "at-boundary", 99L, boundary));

    verify(changes, never()).saveAndFlush(any());
  }

  @Test
  void afterBoundaryRequestCannotCreateStaleSameBoundaryDowngrade() {
    stubNewDowngrade("after-boundary");
    when(periodRepository.findByCompanyIdAndKindAndStartAt(10L, BillingPeriodKind.PLATFORM, boundary))
        .thenReturn(List.of());

    assertThrows(
        ConflictException.class,
        () -> service.scheduleDowngrade(
            10L, 30L, 31L, "after-boundary", 99L, boundary.plusSeconds(1)));
  }

  private SubscriptionChange saved;

  private SubscriptionChange capturedChange() {
    return saved;
  }

  private void stubNewDowngrade(String key) {
    when(companies.findByIdForUpdate(10L)).thenReturn(Optional.of(company));
    when(changes.findByCompanyIdAndIdempotencyKey(10L, key)).thenReturn(Optional.empty());
    when(lifecycle.currentForUpdate(10L)).thenReturn(subscription);
    when(plans.findById(30L)).thenReturn(Optional.of(targetPlan));
    when(prices.findById(31L)).thenReturn(Optional.of(targetPrice));
    when(changes.saveAndFlush(any(SubscriptionChange.class)))
        .thenAnswer(invocation -> {
          saved = invocation.getArgument(0);
          if (saved.getId() == null) saved.setId(900L);
          return saved;
        });
  }

  private BillingPeriod ownedRenewal(Instant end) {
    BillingPeriod period = new BillingPeriod();
    period.setId(700L);
    period.setCompany(company);
    period.setKind(BillingPeriodKind.PLATFORM);
    period.setCadence(BillingCycle.MONTHLY);
    period.setStartAt(boundary);
    period.setEndAt(end);
    period.setPlan(currentPlan);
    period.setPlanPrice(currentPrice);
    period.setPlatformAmountSnapshot(currentPrice.getAmount());
    period.setCurrencySnapshot("INR");
    return period;
  }

  private Plan plan(Long id) {
    Plan plan = new Plan();
    plan.setId(id);
    plan.setStatus(PlanStatus.PUBLISHED);
    plan.setVisibility(PlanVisibility.PUBLIC);
    return plan;
  }

  private PlanPrice price(Long id, Plan plan, String amount, Instant effectiveFrom) {
    PlanPrice price = new PlanPrice();
    price.setId(id);
    price.setPlan(plan);
    price.setBillingCycle(BillingCycle.MONTHLY);
    price.setAmount(new BigDecimal(amount));
    price.setCurrency("INR");
    price.setStatus(RateCardStatus.ACTIVE);
    price.setEffectiveFrom(effectiveFrom);
    return price;
  }
}
