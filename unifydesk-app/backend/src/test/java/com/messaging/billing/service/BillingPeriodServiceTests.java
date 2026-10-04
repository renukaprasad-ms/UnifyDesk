package com.messaging.billing.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.messaging.billing.entity.BillingPeriod;
import com.messaging.billing.entity.CompanySubscription;
import com.messaging.billing.entity.Plan;
import com.messaging.billing.entity.PlanPrice;
import com.messaging.billing.enums.BillingCycle;
import com.messaging.billing.enums.BillingPeriodKind;
import com.messaging.billing.enums.BillingPeriodStatus;
import com.messaging.billing.enums.RateCardStatus;
import com.messaging.billing.repository.BillingPeriodRepository;
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
class BillingPeriodServiceTests {

  @Mock private BillingPeriodRepository periodRepository;
  @Mock private CompanyRepository companyRepository;

  @InjectMocks private BillingPeriodService service;

  @Test
  void platformPeriodReplayReturnsCanonicalRowAndBackfillsFinancialSnapshot() {
    Company company = company(1L);
    Plan plan = plan(2L);
    PlanPrice price = price(3L, plan);
    CompanySubscription subscription = subscription(4L, company, plan, price);
    BillingPeriod existing = period(5L, company, BillingPeriodKind.PLATFORM);
    existing.setStartAt(subscription.getCurrentPeriodStart());
    existing.setEndAt(subscription.getCurrentPeriodEnd());

    when(companyRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(company));
    when(periodRepository.findByCompanyIdAndKindAndStartAtAndEndAt(
            1L,
            BillingPeriodKind.PLATFORM,
            subscription.getCurrentPeriodStart(),
            subscription.getCurrentPeriodEnd()))
        .thenReturn(Optional.of(existing));
    when(periodRepository.saveAndFlush(existing)).thenReturn(existing);

    BillingPeriod result = service.ensurePlatformPeriod(subscription);

    assertSame(existing, result);
    assertEquals(subscription, result.getSubscription());
    assertEquals(plan, result.getPlan());
    assertEquals(price, result.getPlanPrice());
    assertEquals(new BigDecimal("100.00"), result.getPlatformAmountSnapshot());
    assertEquals("INR", result.getCurrencySnapshot());
  }

  @Test
  void preActivationPlatformSnapshotFreezesPlanPriceAmountAndRange() {
    Company company = company(1L);
    Plan plan = plan(2L);
    PlanPrice price = price(3L, plan);
    Instant start = Instant.parse("2026-10-03T06:00:00Z");
    Instant end = Instant.parse("2026-11-03T06:00:00Z");
    BillingPeriod existing = period(5L, company, BillingPeriodKind.PLATFORM);
    existing.setStartAt(start);
    existing.setEndAt(end);

    when(companyRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(company));
    when(periodRepository.findByCompanyIdAndKindAndStartAtAndEndAt(
            1L, BillingPeriodKind.PLATFORM, start, end))
        .thenReturn(Optional.of(existing));
    when(periodRepository.saveAndFlush(existing)).thenReturn(existing);

    BillingPeriod result =
        service.ensurePlatformPeriodSnapshot(company, plan, price, start, end);

    assertEquals(plan, result.getPlan());
    assertEquals(price, result.getPlanPrice());
    assertEquals(new BigDecimal("100.00"), result.getPlatformAmountSnapshot());
    assertEquals("INR", result.getCurrencySnapshot());
    assertEquals(null, result.getSubscription());
  }

  @Test
  void freePlatformSnapshotIsOpenEndedAndKeepsFreeCadence() {
    Company company = company(1L);
    Plan plan = plan(2L);
    plan.setBillingCycle(BillingCycle.FREE);
    plan.setBasePrice(BigDecimal.ZERO);
    PlanPrice price = price(3L, plan);
    price.setBillingCycle(BillingCycle.FREE);
    price.setAmount(BigDecimal.ZERO);
    Instant start = Instant.parse("2026-11-01T00:00:00Z");

    when(companyRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(company));
    when(periodRepository.findByCompanyIdAndKindAndStartAt(
            1L, BillingPeriodKind.PLATFORM, start))
        .thenReturn(List.of(), List.of());
    when(periodRepository.saveAndFlush(any(BillingPeriod.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    BillingPeriod result =
        service.ensurePlatformPeriodSnapshot(company, plan, price, start, null);

    assertEquals(BillingCycle.FREE, result.getCadence());
    assertEquals(start, result.getStartAt());
    assertEquals(null, result.getEndAt());
    assertEquals(plan, result.getPlan());
    assertEquals(price, result.getPlanPrice());
    assertEquals(BigDecimal.ZERO, result.getPlatformAmountSnapshot());
  }

  @Test
  void preCreatedOldPlanBoundaryCannotBeRewrittenForScheduledDowngrade() {
    Company company = company(1L);
    Plan oldPlan = plan(2L);
    Plan targetPlan = plan(3L);
    PlanPrice oldPrice = price(20L, oldPlan);
    PlanPrice targetPrice = price(30L, targetPlan);
    Instant start = Instant.parse("2026-11-01T00:00:00Z");
    Instant end = Instant.parse("2026-12-01T00:00:00Z");

    BillingPeriod oldBoundary = period(5L, company, BillingPeriodKind.PLATFORM);
    oldBoundary.setStartAt(start);
    oldBoundary.setEndAt(end);
    oldBoundary.setPlan(oldPlan);
    oldBoundary.setPlanPrice(oldPrice);
    oldBoundary.setPlatformAmountSnapshot(oldPrice.getAmount());
    oldBoundary.setCurrencySnapshot("INR");

    when(companyRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(company));
    when(periodRepository.findByCompanyIdAndKindAndStartAt(
            1L, BillingPeriodKind.PLATFORM, start))
        .thenReturn(List.of(oldBoundary));

    assertThrows(
        ConflictException.class,
        () -> service.ensurePlatformPeriodSnapshot(company, targetPlan, targetPrice, start, end));

    assertEquals(oldPlan, oldBoundary.getPlan());
    assertEquals(oldPrice, oldBoundary.getPlanPrice());
    verify(periodRepository, never()).saveAndFlush(any(BillingPeriod.class));
  }

  @Test
  void prepaidPlatformSnapshotRejectsCallerControlledPeriodEnd() {
    Company company = company(1L);
    Plan plan = plan(2L);
    PlanPrice price = price(3L, plan);
    Instant start = Instant.parse("2026-10-03T06:00:00Z");
    Instant shortened = Instant.parse("2026-10-20T06:00:00Z");

    assertThrows(
        com.messaging.common.exception.BadRequestException.class,
        () -> service.ensurePlatformPeriodSnapshot(company, plan, price, start, shortened));

    verify(periodRepository, never()).saveAndFlush(any(BillingPeriod.class));
  }

  @Test
  void prepaidPlatformPeriodBindsActivatedSubscriptionWithoutRewritingSnapshot() {
    Company company = company(1L);
    Plan plan = plan(2L);
    PlanPrice price = price(3L, plan);
    CompanySubscription subscription = subscription(4L, company, plan, price);
    BillingPeriod period = period(5L, company, BillingPeriodKind.PLATFORM);
    period.setPlan(plan);
    period.setPlanPrice(price);
    period.setPlatformAmountSnapshot(new BigDecimal("100.00"));
    period.setCurrencySnapshot("INR");
    period.setStartAt(subscription.getCurrentPeriodStart());
    period.setEndAt(subscription.getCurrentPeriodEnd());

    when(periodRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(period));
    when(periodRepository.saveAndFlush(period)).thenReturn(period);

    BillingPeriod bound = service.bindPlatformPeriodToSubscription(5L, subscription);

    assertEquals(subscription, bound.getSubscription());
    assertEquals(plan, bound.getPlan());
    assertEquals(price, bound.getPlanPrice());
    assertEquals(new BigDecimal("100.00"), bound.getPlatformAmountSnapshot());
  }

  @Test
  void incompatibleExistingPlatformSnapshotFailsClosed() {
    Company company = company(1L);
    Plan plan = plan(2L);
    PlanPrice price = price(3L, plan);
    CompanySubscription subscription = subscription(4L, company, plan, price);

    BillingPeriod existing = period(5L, company, BillingPeriodKind.PLATFORM);
    existing.setStartAt(subscription.getCurrentPeriodStart());
    existing.setEndAt(subscription.getCurrentPeriodEnd());
    Plan differentPlan = plan(99L);
    existing.setSubscription(subscription);
    existing.setPlan(differentPlan);
    existing.setPlanPrice(price);
    existing.setPlatformAmountSnapshot(new BigDecimal("100.00"));
    existing.setCurrencySnapshot("INR");

    when(companyRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(company));
    when(periodRepository.findByCompanyIdAndKindAndStartAtAndEndAt(
            1L,
            BillingPeriodKind.PLATFORM,
            subscription.getCurrentPeriodStart(),
            subscription.getCurrentPeriodEnd()))
        .thenReturn(Optional.of(existing));

    assertThrows(
        ConflictException.class, () -> service.ensurePlatformPeriod(subscription));

    verify(periodRepository, never()).saveAndFlush(any(BillingPeriod.class));
  }

  @Test
  void managedUsagePeriodReplayDoesNotInsertDuplicate() {
    Company company = company(1L);
    Instant occurredAt = Instant.parse("2026-10-12T12:00:00Z");
    Instant start = Instant.parse("2026-10-01T00:00:00Z");
    Instant end = Instant.parse("2026-11-01T00:00:00Z");
    BillingPeriod existing = period(5L, company, BillingPeriodKind.MANAGED_USAGE);
    existing.setStartAt(start);
    existing.setEndAt(end);

    when(companyRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(company));
    when(periodRepository.findByCompanyIdAndKindAndStartAtAndEndAt(
            1L, BillingPeriodKind.MANAGED_USAGE, start, end))
        .thenReturn(Optional.of(existing));

    assertSame(existing, service.ensureManagedUsagePeriod(1L, occurredAt));
    verify(periodRepository, never()).saveAndFlush(any(BillingPeriod.class));
  }

  @Test
  void duePeriodClaimReplayConverges() {
    Instant now = Instant.parse("2026-11-01T00:00:00Z");
    BillingPeriod period = period(5L, company(1L), BillingPeriodKind.PLATFORM);
    period.setStatus(BillingPeriodStatus.OPEN);
    when(periodRepository.findDueOpenForUpdate(now, 100))
        .thenReturn(List.of(period), List.of());

    assertEquals(1, service.markDueClosing(now));
    assertEquals(BillingPeriodStatus.CLOSING, period.getStatus());
    assertEquals(0, service.markDueClosing(now));
  }

  private Company company(Long id) {
    Company company = new Company();
    company.setId(id);
    return company;
  }

  private Plan plan(Long id) {
    Plan plan = new Plan();
    plan.setId(id);
    plan.setBillingCycle(BillingCycle.MONTHLY);
    plan.setBasePrice(new BigDecimal("100.00"));
    plan.setCurrency("INR");
    return plan;
  }

  private PlanPrice price(Long id, Plan plan) {
    PlanPrice price = new PlanPrice();
    price.setId(id);
    price.setPlan(plan);
    price.setBillingCycle(BillingCycle.MONTHLY);
    price.setAmount(new BigDecimal("100.00"));
    price.setCurrency("INR");
    price.setEffectiveFrom(Instant.parse("2026-01-01T00:00:00Z"));
    price.setStatus(RateCardStatus.ACTIVE);
    return price;
  }

  private CompanySubscription subscription(
      Long id, Company company, Plan plan, PlanPrice price) {
    CompanySubscription subscription = new CompanySubscription();
    subscription.setId(id);
    subscription.setCompany(company);
    subscription.setPlan(plan);
    subscription.setPlanPrice(price);
    subscription.setCurrentPeriodStart(Instant.parse("2026-10-01T00:00:00Z"));
    subscription.setCurrentPeriodEnd(Instant.parse("2026-11-01T00:00:00Z"));
    return subscription;
  }

  private BillingPeriod period(Long id, Company company, BillingPeriodKind kind) {
    BillingPeriod period = new BillingPeriod();
    period.setId(id);
    period.setCompany(company);
    period.setKind(kind);
    period.setCadence(BillingCycle.MONTHLY);
    period.setStatus(BillingPeriodStatus.OPEN);
    return period;
  }
}
