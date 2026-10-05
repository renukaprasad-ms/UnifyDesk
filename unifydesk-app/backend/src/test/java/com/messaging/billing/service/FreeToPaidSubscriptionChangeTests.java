package com.messaging.billing.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.messaging.billing.dto.UpgradeProrationQuote;
import com.messaging.billing.entity.BillingPeriod;
import com.messaging.billing.entity.CompanySubscription;
import com.messaging.billing.entity.Plan;
import com.messaging.billing.entity.PlanPrice;
import com.messaging.billing.entity.SubscriptionChange;
import com.messaging.billing.enums.BillingCycle;
import com.messaging.billing.enums.BillingPeriodKind;
import com.messaging.billing.enums.BillingPeriodStatus;
import com.messaging.billing.enums.PlanChangeDirection;
import com.messaging.billing.enums.PlanStatus;
import com.messaging.billing.enums.PlanVisibility;
import com.messaging.billing.enums.RateCardStatus;
import com.messaging.billing.enums.SubscriptionChangeStatus;
import com.messaging.billing.enums.SubscriptionChangeType;
import com.messaging.billing.enums.SubscriptionStatus;
import com.messaging.billing.enums.SubscriptionTransitionType;
import com.messaging.billing.repository.BillingPeriodRepository;
import com.messaging.billing.repository.PlanPriceRepository;
import com.messaging.billing.repository.PlanRepository;
import com.messaging.billing.repository.SubscriptionChangeRepository;
import com.messaging.common.exception.ConflictException;
import com.messaging.company.entity.Company;
import com.messaging.company.repository.CompanyRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FreeToPaidSubscriptionChangeTests {
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
  private Plan freePlan;
  private Plan paidPlan;
  private PlanPrice freePrice;
  private PlanPrice paidPrice;
  private CompanySubscription subscription;
  private final Instant requestedAt = Instant.parse("2026-10-04T06:00:00Z");
  private final Instant monthlyEnd = Instant.parse("2026-11-04T06:00:00Z");

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
    freePlan = plan(20L);
    paidPlan = plan(30L);
    freePrice = price(21L, freePlan, BillingCycle.FREE, "0.00");
    paidPrice = price(31L, paidPlan, BillingCycle.MONTHLY, "999.00");
    subscription = new CompanySubscription();
    subscription.setId(40L);
    subscription.setCompany(company);
    subscription.setPlan(freePlan);
    subscription.setPlanPrice(freePrice);
    subscription.setStatus(SubscriptionStatus.ACTIVE);
    subscription.setCurrentPeriodStart(Instant.parse("2026-10-01T00:00:00Z"));
    subscription.setCurrentPeriodEnd(null);
  }

  @Test
  void freeToPaidRequestFreezesQuotedMonthlyPeriodAndLeavesSubscriptionFreeBeforePayment() {
    stubUpgradeRequest(BillingCycle.MONTHLY, "999.00", monthlyEnd);
    AtomicReference<SubscriptionChange> saved = new AtomicReference<>();
    when(changes.saveAndFlush(any(SubscriptionChange.class)))
        .thenAnswer(
            invocation -> {
              SubscriptionChange value = invocation.getArgument(0);
              if (value.getId() == null) value.setId(700L);
              saved.set(value);
              return value;
            });

    var result = service.requestUpgrade(10L, 30L, 31L, "free-paid", 99L, requestedAt);

    assertEquals(new BigDecimal("999.00"), result.amountDue());
    assertEquals(new BigDecimal("0.00"), result.unusedCurrentCredit());
    assertEquals(requestedAt, saved.get().getCurrentPeriodStart());
    assertEquals(monthlyEnd, saved.get().getCurrentPeriodEnd());
    assertEquals(freePlan, subscription.getPlan());
    assertEquals(null, subscription.getCurrentPeriodEnd());
  }

  @Test
  void confirmedFreeToPaidCreatesAndBindsQuotedMonthlyPeriodExactlyOnce() {
    Instant confirmedAt = Instant.parse("2026-10-04T06:05:00Z");
    Instant replayAt = Instant.parse("2026-10-04T06:10:00Z");
    SubscriptionChange change = upgradeChange(BillingCycle.MONTHLY, monthlyEnd);
    BillingPeriod period = paidPeriod(BillingCycle.MONTHLY, requestedAt, monthlyEnd);
    when(changes.findByIdForUpdate(700L)).thenReturn(Optional.of(change));
    when(lifecycle.requireForUpdate(40L)).thenReturn(subscription);
    when(periods.ensurePlatformPeriodSnapshot(company, paidPlan, paidPrice, requestedAt, monthlyEnd))
        .thenReturn(period);
    when(changes.saveAndFlush(change)).thenReturn(change);

    var applied = service.applyConfirmedUpgrade(700L, 900L, confirmedAt);
    var replay = service.applyConfirmedUpgrade(700L, 900L, replayAt);

    verify(upgradeInvoices, times(1)).assertPaidCanonicalInvoice(change, 900L);
    verify(periods, times(1))
        .ensurePlatformPeriodSnapshot(company, paidPlan, paidPrice, requestedAt, monthlyEnd);
    verify(periods, never())
        .ensurePlatformPeriodSnapshot(company, paidPlan, paidPrice, confirmedAt, any());
    verify(periods, times(1)).bindPlatformPeriodToSubscription(800L, subscription);
    verify(lifecycle, times(1))
        .recordPlanChange(
            subscription,
            freePlan,
            paidPlan,
            SubscriptionTransitionType.UPGRADED,
            null,
            "Provider-confirmed FREE to paid upgrade applied",
            confirmedAt);
    assertEquals(SubscriptionChangeStatus.APPLIED, applied.status());
    assertEquals(SubscriptionChangeStatus.APPLIED, replay.status());
    assertEquals(confirmedAt, change.getAppliedAt());
    assertEquals(paidPlan, subscription.getPlan());
    assertEquals(paidPrice, subscription.getPlanPrice());
    assertEquals(requestedAt, subscription.getCurrentPeriodStart());
    assertEquals(monthlyEnd, subscription.getCurrentPeriodEnd());
    assertEquals(requestedAt, subscription.getBillingAnchor());
  }

  @Test
  void confirmedFreeToPaidYearlyUsesQuotedPeriodNotConfirmationTime() {
    Instant confirmedAt = Instant.parse("2026-10-04T06:05:00Z");
    Instant yearlyEnd = Instant.parse("2027-10-04T06:00:00Z");
    paidPrice.setBillingCycle(BillingCycle.YEARLY);
    paidPrice.setAmount(new BigDecimal("9999.00"));
    SubscriptionChange change = upgradeChange(BillingCycle.YEARLY, yearlyEnd);
    BillingPeriod period = paidPeriod(BillingCycle.YEARLY, requestedAt, yearlyEnd);
    when(changes.findByIdForUpdate(700L)).thenReturn(Optional.of(change));
    when(lifecycle.requireForUpdate(40L)).thenReturn(subscription);
    when(periods.ensurePlatformPeriodSnapshot(company, paidPlan, paidPrice, requestedAt, yearlyEnd))
        .thenReturn(period);
    when(changes.saveAndFlush(change)).thenReturn(change);

    service.applyConfirmedUpgrade(700L, 900L, confirmedAt);

    verify(upgradeInvoices).assertPaidCanonicalInvoice(change, 900L);
    verify(periods).ensurePlatformPeriodSnapshot(company, paidPlan, paidPrice, requestedAt, yearlyEnd);
    assertEquals(requestedAt, subscription.getCurrentPeriodStart());
    assertEquals(yearlyEnd, subscription.getCurrentPeriodEnd());
    assertEquals(requestedAt, subscription.getBillingAnchor());
    assertEquals(confirmedAt, change.getAppliedAt());
  }

  @Test
  void malformedFreeToPaidMissingQuotedStartFailsClosed() {
    SubscriptionChange change = upgradeChange(BillingCycle.MONTHLY, monthlyEnd);
    change.setCurrentPeriodStart(null);
    stubConfirmation(change);

    assertThrows(
        ConflictException.class,
        () -> service.applyConfirmedUpgrade(700L, 900L, requestedAt.plusSeconds(300)));

    verify(periods, never()).ensurePlatformPeriodSnapshot(any(), any(), any(), any(), any());
  }

  @Test
  void malformedFreeToPaidMissingQuotedEndFailsClosed() {
    SubscriptionChange change = upgradeChange(BillingCycle.MONTHLY, monthlyEnd);
    change.setCurrentPeriodEnd(null);
    stubConfirmation(change);

    assertThrows(
        ConflictException.class,
        () -> service.applyConfirmedUpgrade(700L, 900L, requestedAt.plusSeconds(300)));

    verify(periods, never()).ensurePlatformPeriodSnapshot(any(), any(), any(), any(), any());
  }

  @Test
  void malformedFreeToPaidNonIncreasingQuotedPeriodFailsClosed() {
    SubscriptionChange change = upgradeChange(BillingCycle.MONTHLY, requestedAt);
    stubConfirmation(change);

    assertThrows(
        ConflictException.class,
        () -> service.applyConfirmedUpgrade(700L, 900L, requestedAt.plusSeconds(300)));

    verify(periods, never()).ensurePlatformPeriodSnapshot(any(), any(), any(), any(), any());
  }

  @Test
  void positiveDueFreeToPaidStillRequiresCanonicalPaidInvoice() {
    SubscriptionChange change = upgradeChange(BillingCycle.MONTHLY, monthlyEnd);
    when(changes.findByIdForUpdate(700L)).thenReturn(Optional.of(change));

    assertThrows(
        ConflictException.class,
        () -> service.applyConfirmedUpgrade(700L, null, requestedAt.plusSeconds(300)));

    verify(upgradeInvoices, never()).assertPaidCanonicalInvoice(any(), anyLong());
    verify(lifecycle, never()).requireForUpdate(anyLong());
    verify(periods, never()).ensurePlatformPeriodSnapshot(any(), any(), any(), any(), any());
  }

  private void stubUpgradeRequest(BillingCycle cycle, String amount, Instant periodEnd) {
    paidPrice.setBillingCycle(cycle);
    paidPrice.setAmount(new BigDecimal(amount));
    when(companies.findByIdForUpdate(10L)).thenReturn(Optional.of(company));
    when(changes.findByCompanyIdAndIdempotencyKey(10L, "free-paid")).thenReturn(Optional.empty());
    when(lifecycle.currentForUpdate(10L)).thenReturn(subscription);
    when(plans.findById(30L)).thenReturn(Optional.of(paidPlan));
    when(prices.findById(31L)).thenReturn(Optional.of(paidPrice));
    when(classifier.classify(freePlan, freePrice, paidPlan, paidPrice))
        .thenReturn(PlanChangeDirection.UPGRADE);
    UpgradeProrationQuote quote =
        new UpgradeProrationQuote(
            "INR",
            requestedAt,
            requestedAt,
            periodEnd,
            new BigDecimal("0.00"),
            paidPrice.getAmount(),
            paidPrice.getAmount(),
            new BigDecimal("0.00"),
            paidPrice.getAmount(),
            paidPrice.getAmount(),
            new BigDecimal("0.00"));
    when(proration.quoteFromFree(
            new BigDecimal("0.00"), paidPrice.getAmount(), cycle, requestedAt, "INR"))
        .thenReturn(quote);
  }

  private void stubConfirmation(SubscriptionChange change) {
    when(changes.findByIdForUpdate(700L)).thenReturn(Optional.of(change));
    when(lifecycle.requireForUpdate(40L)).thenReturn(subscription);
  }

  private BillingPeriod paidPeriod(BillingCycle cycle, Instant start, Instant end) {
    BillingPeriod period = new BillingPeriod();
    period.setId(800L);
    period.setCompany(company);
    period.setKind(BillingPeriodKind.PLATFORM);
    period.setCadence(cycle);
    period.setStartAt(start);
    period.setEndAt(end);
    period.setStatus(BillingPeriodStatus.OPEN);
    period.setPlan(paidPlan);
    period.setPlanPrice(paidPrice);
    period.setPlatformAmountSnapshot(paidPrice.getAmount());
    period.setCurrencySnapshot("INR");
    return period;
  }

  private SubscriptionChange upgradeChange(BillingCycle cycle, Instant periodEnd) {
    paidPrice.setBillingCycle(cycle);
    SubscriptionChange change = new SubscriptionChange();
    change.setId(700L);
    change.setCompany(company);
    change.setSubscription(subscription);
    change.setFromPlan(freePlan);
    change.setFromPlanPrice(freePrice);
    change.setToPlan(paidPlan);
    change.setToPlanPrice(paidPrice);
    change.setChangeType(SubscriptionChangeType.UPGRADE);
    change.setStatus(SubscriptionChangeStatus.PENDING_PAYMENT);
    change.setRequestedAt(requestedAt);
    change.setEffectiveAt(requestedAt);
    change.setIdempotencyKey("free-paid");
    change.setQuoteReference("UPG-700");
    change.setCurrency("INR");
    change.setCurrentPriceSnapshot(new BigDecimal("0.00"));
    change.setTargetPriceSnapshot(paidPrice.getAmount());
    change.setCurrentPeriodStart(requestedAt);
    change.setCurrentPeriodEnd(periodEnd);
    change.setTargetProratedCharge(paidPrice.getAmount());
    change.setUnusedCurrentCredit(new BigDecimal("0.00"));
    change.setNetAmount(paidPrice.getAmount());
    change.setAmountDue(paidPrice.getAmount());
    change.setCreditAmount(new BigDecimal("0.00"));
    return change;
  }

  private Plan plan(Long id) {
    Plan plan = new Plan();
    plan.setId(id);
    plan.setStatus(PlanStatus.PUBLISHED);
    plan.setVisibility(PlanVisibility.PUBLIC);
    return plan;
  }

  private PlanPrice price(Long id, Plan plan, BillingCycle cycle, String amount) {
    PlanPrice price = new PlanPrice();
    price.setId(id);
    price.setPlan(plan);
    price.setBillingCycle(cycle);
    price.setAmount(new BigDecimal(amount));
    price.setCurrency("INR");
    price.setStatus(RateCardStatus.ACTIVE);
    price.setEffectiveFrom(Instant.parse("2026-01-01T00:00:00Z"));
    return price;
  }
}
