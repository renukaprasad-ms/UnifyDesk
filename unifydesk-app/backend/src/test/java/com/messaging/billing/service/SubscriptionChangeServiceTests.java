package com.messaging.billing.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.messaging.billing.dto.UpgradeProrationQuote;
import com.messaging.billing.entity.CompanySubscription;
import com.messaging.billing.entity.Plan;
import com.messaging.billing.entity.PlanPrice;
import com.messaging.billing.entity.SubscriptionChange;
import com.messaging.billing.enums.BillingCycle;
import com.messaging.billing.enums.PlanChangeDirection;
import com.messaging.billing.enums.PlanStatus;
import com.messaging.billing.enums.PlanVisibility;
import com.messaging.billing.enums.RateCardStatus;
import com.messaging.billing.enums.SubscriptionChangeStatus;
import com.messaging.billing.enums.SubscriptionChangeType;
import com.messaging.billing.enums.SubscriptionStatus;
import com.messaging.billing.repository.PlanPriceRepository;
import com.messaging.billing.repository.PlanRepository;
import com.messaging.billing.repository.SubscriptionChangeRepository;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.ConflictException;
import com.messaging.company.entity.Company;
import com.messaging.company.repository.CompanyRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SubscriptionChangeServiceTests {

  @Mock private SubscriptionChangeRepository changeRepository;
  @Mock private PlanRepository planRepository;
  @Mock private PlanPriceRepository planPriceRepository;
  @Mock private CompanyRepository companyRepository;
  @Mock private PlanEligibilityService eligibilityService;
  @Mock private SubscriptionLifecycleService lifecycleService;
  @Mock private UpgradeProrationCalculator prorationCalculator;
  @Mock private PlanChangeClassifier planChangeClassifier;
  @Mock private BillingPeriodService billingPeriodService;
  @Mock private UpgradeInvoiceService upgradeInvoiceService;

  @InjectMocks private SubscriptionChangeService service;

  private Company company;
  private Plan currentPlan;
  private Plan targetPlan;
  private PlanPrice currentPrice;
  private PlanPrice targetPrice;
  private CompanySubscription current;
  private Instant now;
  private Instant periodEnd;

  @BeforeEach
  void setUp() {
    now = Instant.parse("2026-10-03T06:00:00Z");
    periodEnd = Instant.parse("2026-11-01T00:00:00Z");
    company = company(10L);
    currentPlan = plan(20L);
    targetPlan = plan(30L);
    currentPrice = price(21L, currentPlan, "1000");
    targetPrice = price(31L, targetPlan, "2000");
    current = subscription(40L, company, currentPlan, currentPrice);
  }

  @Test
  void expiredUpgradeTargetPriceIsRejected() {
    targetPrice.setEffectiveFrom(now.minusSeconds(3600));
    targetPrice.setEffectiveTo(now);

    commonNewCommandStubs("expired-upgrade");

    assertThrows(
        BadRequestException.class,
        () -> service.requestUpgrade(10L, 30L, 31L, "expired-upgrade", 99L, now));

    verify(planChangeClassifier, never()).classify(any(), any(), any(), any());
  }

  @Test
  void futureUpgradeTargetPriceIsRejected() {
    targetPrice.setEffectiveFrom(now.plusSeconds(1));

    commonNewCommandStubs("future-upgrade");

    assertThrows(
        BadRequestException.class,
        () -> service.requestUpgrade(10L, 30L, 31L, "future-upgrade", 99L, now));
  }

  @Test
  void currentUpgradeTargetPriceIsAcceptedAtInclusiveStartBoundary() {
    targetPrice.setEffectiveFrom(now);
    targetPrice.setEffectiveTo(now.plusSeconds(3600));
    commonNewCommandStubs("valid-upgrade");
    when(planChangeClassifier.classify(currentPlan, currentPrice, targetPlan, targetPrice))
        .thenReturn(PlanChangeDirection.UPGRADE);
    when(prorationCalculator.quote(
            currentPrice.getAmount(),
            targetPrice.getAmount(),
            BillingCycle.MONTHLY,
            current.getCurrentPeriodStart(),
            current.getCurrentPeriodEnd(),
            now,
            "INR"))
        .thenReturn(quote("500.00"));
    stubSaveAssigningId();

    assertEquals(
        SubscriptionChangeStatus.PENDING_PAYMENT,
        service.requestUpgrade(10L, 30L, 31L, "valid-upgrade", 99L, now).status());
  }

  @Test
  void downgradePriceIsValidatedAtEffectiveBoundaryNotRequestTime() {
    targetPrice.setEffectiveFrom(periodEnd);
    targetPrice.setEffectiveTo(periodEnd.plusSeconds(3600));
    commonNewCommandStubs("future-at-request-valid-at-boundary");
    when(planChangeClassifier.classify(currentPlan, currentPrice, targetPlan, targetPrice))
        .thenReturn(PlanChangeDirection.DOWNGRADE);
    stubSaveAssigningId();

    var result =
        service.scheduleDowngrade(
            10L, 30L, 31L, "future-at-request-valid-at-boundary", 99L, now);

    assertEquals(periodEnd, result.effectiveAt());
  }

  @Test
  void downgradePriceUsesHalfOpenEffectiveEndBoundary() {
    targetPrice.setEffectiveFrom(now.minusSeconds(3600));
    targetPrice.setEffectiveTo(periodEnd);
    commonNewCommandStubs("end-exclusive");

    assertThrows(
        BadRequestException.class,
        () -> service.scheduleDowngrade(10L, 30L, 31L, "end-exclusive", 99L, now));
  }

  @Test
  void lowerPlanCannotUseUpgradePath() {
    commonNewCommandStubs("same-key");
    when(planChangeClassifier.classify(currentPlan, currentPrice, targetPlan, targetPrice))
        .thenReturn(PlanChangeDirection.DOWNGRADE);

    assertThrows(
        ConflictException.class,
        () -> service.requestUpgrade(10L, 30L, 31L, "same-key", 99L, now));

    verify(changeRepository, never()).saveAndFlush(any());
  }

  @Test
  void higherPlanCannotUseDowngradePath() {
    commonNewCommandStubs("same-key");
    when(planChangeClassifier.classify(currentPlan, currentPrice, targetPlan, targetPrice))
        .thenReturn(PlanChangeDirection.UPGRADE);

    assertThrows(
        ConflictException.class,
        () -> service.scheduleDowngrade(10L, 30L, 31L, "same-key", 99L, now));

    verify(changeRepository, never()).saveAndFlush(any());
  }

  @Test
  void positiveDueUpgradeRemainsPaymentGated() {
    commonNewCommandStubs("upgrade-1");
    when(planChangeClassifier.classify(currentPlan, currentPrice, targetPlan, targetPrice))
        .thenReturn(PlanChangeDirection.UPGRADE);
    when(prorationCalculator.quote(
            currentPrice.getAmount(),
            targetPrice.getAmount(),
            BillingCycle.MONTHLY,
            current.getCurrentPeriodStart(),
            current.getCurrentPeriodEnd(),
            now,
            "INR"))
        .thenReturn(quote("500.00"));
    stubSaveAssigningId();

    var response = service.requestUpgrade(10L, 30L, 31L, "upgrade-1", 99L, now);

    assertEquals(SubscriptionChangeStatus.PENDING_PAYMENT, response.status());
    assertEquals(new BigDecimal("500.00"), response.amountDue());
    assertEquals(20L, current.getPlan().getId());
  }

  @Test
  void paidUpgradeCannotApplyWithoutConfirmedFinancialEvidence() {
    SubscriptionChange change = prior(10L, "upgrade-1", SubscriptionChangeType.UPGRADE, 31L);
    change.setStatus(SubscriptionChangeStatus.PENDING_PAYMENT);
    change.setAmountDue(new BigDecimal("500.00"));
    when(changeRepository.findByIdForUpdate(change.getId())).thenReturn(Optional.of(change));

    assertThrows(
        ConflictException.class,
        () -> service.applyConfirmedUpgrade(change.getId(), null, now));

    verify(lifecycleService, never()).requireForUpdate(any());
  }

  @Test
  void downgradeIsScheduledExactlyAtCurrentBoundary() {
    commonNewCommandStubs("down-1");
    when(planChangeClassifier.classify(currentPlan, currentPrice, targetPlan, targetPrice))
        .thenReturn(PlanChangeDirection.DOWNGRADE);
    stubSaveAssigningId();

    var response = service.scheduleDowngrade(10L, 30L, 31L, "down-1", 99L, now);

    assertEquals(SubscriptionChangeStatus.SCHEDULED, response.status());
    assertEquals(periodEnd, response.effectiveAt());
    assertEquals(20L, current.getPlan().getId());
  }

  @Test
  void scheduledDowngradeClaimReplayConvergesOnce() {
    SubscriptionChange change = prior(10L, "down-due", SubscriptionChangeType.DOWNGRADE, 31L);
    change.setStatus(SubscriptionChangeStatus.SCHEDULED);
    change.setEffectiveAt(periodEnd);
    when(changeRepository.findDueDowngradesForUpdate(periodEnd, 100))
        .thenReturn(java.util.List.of(change), java.util.List.of());
    when(lifecycleService.requireForUpdate(40L)).thenReturn(current);

    assertEquals(1, service.applyDueDowngrades(periodEnd));
    assertEquals(SubscriptionChangeStatus.APPLIED, change.getStatus());
    assertEquals(targetPlan, current.getPlan());
    assertEquals(0, service.applyDueDowngrades(periodEnd));

    verify(lifecycleService, times(1))
        .recordPlanChange(
            any(CompanySubscription.class),
            any(Plan.class),
            any(Plan.class),
            any(),
            any(),
            any(),
            any());
  }

  @Test
  void exactIdempotentReplayReturnsOriginalChange() {
    SubscriptionChange prior = prior(10L, "replay", SubscriptionChangeType.UPGRADE, 31L);
    when(companyRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(company));
    when(changeRepository.findByCompanyIdAndIdempotencyKey(10L, "replay"))
        .thenReturn(Optional.of(prior));

    var response = service.requestUpgrade(10L, 30L, 31L, "replay", 99L, now.plusSeconds(30));

    assertEquals(prior.getId().toString(), response.changeId());
    verify(lifecycleService, never()).currentForUpdate(any());
  }

  @Test
  void sameKeyWithDifferentTargetPriceConflicts() {
    SubscriptionChange prior = prior(10L, "replay", SubscriptionChangeType.UPGRADE, 31L);
    when(companyRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(company));
    when(changeRepository.findByCompanyIdAndIdempotencyKey(10L, "replay"))
        .thenReturn(Optional.of(prior));

    assertThrows(
        ConflictException.class,
        () -> service.requestUpgrade(10L, 30L, 32L, "replay", 99L, now));
  }

  @Test
  void sameKeyWithDifferentOperationConflicts() {
    SubscriptionChange prior = prior(10L, "replay", SubscriptionChangeType.UPGRADE, 31L);
    when(companyRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(company));
    when(changeRepository.findByCompanyIdAndIdempotencyKey(10L, "replay"))
        .thenReturn(Optional.of(prior));

    assertThrows(
        ConflictException.class,
        () -> service.scheduleDowngrade(10L, 30L, 31L, "replay", 99L, now));
  }

  @Test
  void sameRawKeyInSeparateCompanyDoesNotCollide() {
    Company companyTwo = company(11L);
    CompanySubscription currentTwo = subscription(41L, companyTwo, currentPlan, currentPrice);
    when(companyRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(companyTwo));
    when(changeRepository.findByCompanyIdAndIdempotencyKey(11L, "shared-key"))
        .thenReturn(Optional.empty());
    when(lifecycleService.currentForUpdate(11L)).thenReturn(currentTwo);
    when(planRepository.findById(30L)).thenReturn(Optional.of(targetPlan));
    when(planPriceRepository.findById(31L)).thenReturn(Optional.of(targetPrice));
    when(planChangeClassifier.classify(currentPlan, currentPrice, targetPlan, targetPrice))
        .thenReturn(PlanChangeDirection.UPGRADE);
    when(prorationCalculator.quote(
            currentPrice.getAmount(),
            targetPrice.getAmount(),
            BillingCycle.MONTHLY,
            currentTwo.getCurrentPeriodStart(),
            currentTwo.getCurrentPeriodEnd(),
            now,
            "INR"))
        .thenReturn(quote("500.00"));
    stubSaveAssigningId();

    var response = service.requestUpgrade(11L, 30L, 31L, "shared-key", 199L, now);

    assertEquals("11", response.companyId());
    verify(changeRepository).findByCompanyIdAndIdempotencyKey(11L, "shared-key");
  }

  private void commonNewCommandStubs(String key) {
    when(companyRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(company));
    when(changeRepository.findByCompanyIdAndIdempotencyKey(10L, key))
        .thenReturn(Optional.empty());
    when(lifecycleService.currentForUpdate(10L)).thenReturn(current);
    when(planRepository.findById(30L)).thenReturn(Optional.of(targetPlan));
    when(planPriceRepository.findById(31L)).thenReturn(Optional.of(targetPrice));
  }

  private void stubSaveAssigningId() {
    when(changeRepository.saveAndFlush(any(SubscriptionChange.class)))
        .thenAnswer(
            invocation -> {
              SubscriptionChange value = invocation.getArgument(0);
              if (value.getId() == null) {
                value.setId(900L);
              }
              return value;
            });
  }

  private UpgradeProrationQuote quote(String amountDue) {
    return new UpgradeProrationQuote(
        "INR",
        now,
        current.getCurrentPeriodStart(),
        current.getCurrentPeriodEnd(),
        currentPrice.getAmount(),
        targetPrice.getAmount(),
        new BigDecimal("1000.00"),
        new BigDecimal("500.00"),
        new BigDecimal(amountDue),
        new BigDecimal(amountDue),
        BigDecimal.ZERO.setScale(2));
  }

  private SubscriptionChange prior(
      Long companyId, String key, SubscriptionChangeType type, Long targetPriceId) {
    Company owner = company(companyId);
    PlanPrice priorTargetPrice = price(targetPriceId, targetPlan, "2000");
    SubscriptionChange change = new SubscriptionChange();
    change.setId(700L);
    change.setCompany(owner);
    change.setSubscription(subscription(40L, owner, currentPlan, currentPrice));
    change.setFromPlan(currentPlan);
    change.setToPlan(targetPlan);
    change.setFromPlanPrice(currentPrice);
    change.setToPlanPrice(priorTargetPrice);
    change.setChangeType(type);
    change.setStatus(SubscriptionChangeStatus.PENDING_PAYMENT);
    change.setRequestedAt(now);
    change.setEffectiveAt(now);
    change.setIdempotencyKey(key);
    change.setCurrency("INR");
    change.setAmountDue(new BigDecimal("500.00"));
    return change;
  }

  private Company company(Long id) {
    Company value = new Company();
    value.setId(id);
    return value;
  }

  private Plan plan(Long id) {
    Plan value = new Plan();
    value.setId(id);
    value.setStatus(PlanStatus.PUBLISHED);
    value.setVisibility(PlanVisibility.PUBLIC);
    return value;
  }

  private PlanPrice price(Long id, Plan plan, String amount) {
    PlanPrice value = new PlanPrice();
    value.setId(id);
    value.setPlan(plan);
    value.setBillingCycle(BillingCycle.MONTHLY);
    value.setAmount(new BigDecimal(amount));
    value.setCurrency("INR");
    value.setStatus(RateCardStatus.ACTIVE);
    value.setEffectiveFrom(now == null ? Instant.parse("2026-01-01T00:00:00Z") : now.minusSeconds(86400));
    return value;
  }

  private CompanySubscription subscription(
      Long id, Company owner, Plan plan, PlanPrice price) {
    CompanySubscription value = new CompanySubscription();
    value.setId(id);
    value.setCompany(owner);
    value.setPlan(plan);
    value.setPlanPrice(price);
    value.setStatus(SubscriptionStatus.ACTIVE);
    value.setCurrentPeriodStart(Instant.parse("2026-10-01T00:00:00Z"));
    value.setCurrentPeriodEnd(periodEnd);
    return value;
  }
}
