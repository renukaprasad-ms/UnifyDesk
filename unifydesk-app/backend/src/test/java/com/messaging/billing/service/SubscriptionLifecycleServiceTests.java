package com.messaging.billing.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.messaging.billing.entity.BillingPeriod;
import com.messaging.billing.entity.CompanySubscription;
import com.messaging.billing.entity.Plan;
import com.messaging.billing.entity.PlanPrice;
import com.messaging.billing.entity.SubscriptionChange;
import com.messaging.billing.entity.SubscriptionTransition;
import com.messaging.billing.enums.BillingCycle;
import com.messaging.billing.enums.BillingPeriodKind;
import com.messaging.billing.enums.BillingPeriodStatus;
import com.messaging.billing.enums.RateCardStatus;
import com.messaging.billing.enums.SubscriptionChangeStatus;
import com.messaging.billing.enums.SubscriptionStatus;
import com.messaging.billing.enums.SubscriptionTransitionType;
import com.messaging.billing.repository.CompanySubscriptionRepository;
import com.messaging.billing.repository.SubscriptionTransitionRepository;
import com.messaging.common.exception.ConflictException;
import com.messaging.company.entity.Company;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SubscriptionLifecycleServiceTests {

  @Mock private CompanySubscriptionRepository subscriptionRepository;
  @Mock private SubscriptionTransitionRepository transitionRepository;
  @Mock private BillingPeriodService billingPeriodService;
  @Mock private InvoiceService invoiceService;
  @Mock private SubscriptionBoundaryChangeService boundaryChangeService;

  @InjectMocks private SubscriptionLifecycleService service;

  @Test
  void activatingSubscriptionEnsuresCanonicalPlatformPeriod() {
    CompanySubscription subscription = subscription(10L, SubscriptionStatus.ACTIVE);
    subscription.setCurrentPeriodEnd(Instant.parse("2026-11-01T00:00:00Z"));

    service.recordCreated(
        subscription, 77L, "activated", SubscriptionTransitionType.CREATED);

    verify(billingPeriodService).ensurePlatformPeriod(subscription);
    verify(transitionRepository).save(any(SubscriptionTransition.class));
  }

  @Test
  void confirmedRenewalRequiresCanonicalPaidInvoiceAndBindsNextPeriod() {
    Instant confirmedAt = Instant.parse("2026-11-01T00:01:00Z");
    CompanySubscription subscription = subscription(10L, SubscriptionStatus.ACTIVE);
    subscription.setCurrentPeriodEnd(Instant.parse("2026-11-01T00:00:00Z"));

    PlanPrice currentPrice = new PlanPrice();
    currentPrice.setId(20L);
    currentPrice.setPlan(subscription.getPlan());
    currentPrice.setBillingCycle(BillingCycle.MONTHLY);
    currentPrice.setAmount(new java.math.BigDecimal("100.00"));
    currentPrice.setCurrency("INR");
    currentPrice.setEffectiveFrom(Instant.parse("2026-01-01T00:00:00Z"));
    currentPrice.setStatus(RateCardStatus.ACTIVE);
    subscription.setPlanPrice(currentPrice);

    PlanPrice renewalPrice = new PlanPrice();
    renewalPrice.setId(21L);
    renewalPrice.setPlan(subscription.getPlan());
    renewalPrice.setBillingCycle(BillingCycle.MONTHLY);
    renewalPrice.setAmount(new java.math.BigDecimal("120.00"));
    renewalPrice.setCurrency("INR");
    renewalPrice.setEffectiveFrom(Instant.parse("2026-01-01T00:00:00Z"));
    renewalPrice.setStatus(RateCardStatus.ACTIVE);

    BillingPeriod next = new BillingPeriod();
    next.setId(30L);
    next.setCompany(subscription.getCompany());
    next.setKind(BillingPeriodKind.PLATFORM);
    next.setCadence(BillingCycle.MONTHLY);
    next.setPlan(subscription.getPlan());
    next.setPlanPrice(renewalPrice);
    next.setPlatformAmountSnapshot(new java.math.BigDecimal("120.00"));
    next.setCurrencySnapshot("INR");
    next.setStartAt(Instant.parse("2026-11-01T00:00:00Z"));
    next.setEndAt(Instant.parse("2026-12-01T00:00:00Z"));
    next.setStatus(BillingPeriodStatus.OPEN);

    when(subscriptionRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(subscription));
    when(invoiceService.requirePaidCanonicalPlatformInvoice(1L, 900L)).thenReturn(next);
    when(subscriptionRepository.saveAndFlush(subscription)).thenReturn(subscription);

    CompanySubscription renewed =
        service.renewAfterConfirmedCollection(10L, 900L, confirmedAt);

    assertEquals(next.getStartAt(), renewed.getCurrentPeriodStart());
    assertEquals(next.getEndAt(), renewed.getCurrentPeriodEnd());
    assertEquals(renewalPrice, renewed.getPlanPrice());
    assertEquals(SubscriptionStatus.ACTIVE, renewed.getStatus());
    verify(billingPeriodService).bindPlatformPeriodToSubscription(30L, subscription);
    verify(transitionRepository).save(any(SubscriptionTransition.class));
  }

  @Test
  void providerConfirmedRenewalAtomicallyAppliesScheduledDowngradeTarget() {
    Instant boundary = Instant.parse("2026-11-01T00:00:00Z");
    Instant confirmedAt = boundary.plusSeconds(1);
    CompanySubscription subscription = subscription(10L, SubscriptionStatus.ACTIVE);
    subscription.setCurrentPeriodEnd(boundary);
    PlanPrice currentPrice = priced(20L, subscription.getPlan(), "120.00");
    subscription.setPlanPrice(currentPrice);

    Plan targetPlan = new Plan();
    targetPlan.setId(3L);
    PlanPrice targetPrice = priced(21L, targetPlan, "80.00");
    BillingPeriod next = paidPeriod(subscription, targetPlan, targetPrice, boundary);

    SubscriptionChange downgrade = new SubscriptionChange();
    downgrade.setId(88L);
    downgrade.setSubscription(subscription);
    downgrade.setFromPlan(subscription.getPlan());
    downgrade.setFromPlanPrice(currentPrice);
    downgrade.setToPlan(targetPlan);
    downgrade.setToPlanPrice(targetPrice);
    downgrade.setCurrentPeriodStart(subscription.getCurrentPeriodStart());
    downgrade.setCurrentPeriodEnd(boundary);
    downgrade.setEffectiveAt(boundary);
    downgrade.setStatus(SubscriptionChangeStatus.SCHEDULED);
    downgrade.setRequestedByUserId(77L);

    when(subscriptionRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(subscription));
    when(invoiceService.requirePaidCanonicalPlatformInvoice(1L, 900L)).thenReturn(next);
    when(boundaryChangeService.resolveScheduledDowngradeForBoundary(subscription, boundary))
        .thenReturn(Optional.of(downgrade));
    when(subscriptionRepository.saveAndFlush(subscription)).thenReturn(subscription);

    CompanySubscription renewed =
        service.renewAfterConfirmedCollection(10L, 900L, confirmedAt);

    assertEquals(targetPlan, renewed.getPlan());
    assertEquals(targetPrice, renewed.getPlanPrice());
    assertEquals(boundary, renewed.getCurrentPeriodStart());
    verify(boundaryChangeService).markApplied(downgrade, 900L, confirmedAt);
    verify(billingPeriodService).bindPlatformPeriodToSubscription(next.getId(), subscription);
  }

  @Test
  void wrongPlanPaidInvoiceCannotActivateScheduledDowngrade() {
    Instant boundary = Instant.parse("2026-11-01T00:00:00Z");
    CompanySubscription subscription = subscription(10L, SubscriptionStatus.ACTIVE);
    subscription.setCurrentPeriodEnd(boundary);
    PlanPrice currentPrice = priced(20L, subscription.getPlan(), "120.00");
    subscription.setPlanPrice(currentPrice);

    Plan targetPlan = new Plan();
    targetPlan.setId(3L);
    PlanPrice targetPrice = priced(21L, targetPlan, "80.00");
    Plan oldPlan = subscription.getPlan();
    PlanPrice oldRenewalPrice = priced(22L, oldPlan, "120.00");
    BillingPeriod wrong = paidPeriod(subscription, oldPlan, oldRenewalPrice, boundary);

    SubscriptionChange downgrade = new SubscriptionChange();
    downgrade.setId(88L);
    downgrade.setSubscription(subscription);
    downgrade.setFromPlan(oldPlan);
    downgrade.setFromPlanPrice(currentPrice);
    downgrade.setToPlan(targetPlan);
    downgrade.setToPlanPrice(targetPrice);
    downgrade.setCurrentPeriodStart(subscription.getCurrentPeriodStart());
    downgrade.setCurrentPeriodEnd(boundary);
    downgrade.setEffectiveAt(boundary);
    downgrade.setStatus(SubscriptionChangeStatus.SCHEDULED);

    when(subscriptionRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(subscription));
    when(invoiceService.requirePaidCanonicalPlatformInvoice(1L, 900L)).thenReturn(wrong);
    when(boundaryChangeService.resolveScheduledDowngradeForBoundary(subscription, boundary))
        .thenReturn(Optional.of(downgrade));

    assertThrows(
        ConflictException.class,
        () -> service.renewAfterConfirmedCollection(10L, 900L, boundary.plusSeconds(1)));

    verify(subscriptionRepository, org.mockito.Mockito.never()).saveAndFlush(any());
    verify(boundaryChangeService, org.mockito.Mockito.never()).markApplied(any(), any(), any());
  }

  @Test
  void confirmedRenewalRejectsUnpaidInvoiceBeforeMutatingSubscription() {
    CompanySubscription subscription = subscription(10L, SubscriptionStatus.ACTIVE);
    subscription.setCurrentPeriodEnd(Instant.parse("2026-11-01T00:00:00Z"));

    when(subscriptionRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(subscription));
    when(invoiceService.requirePaidCanonicalPlatformInvoice(1L, 900L))
        .thenThrow(new ConflictException("not paid"));

    assertThrows(
        ConflictException.class,
        () ->
            service.renewAfterConfirmedCollection(
                10L, 900L, Instant.parse("2026-11-01T00:01:00Z")));

    verify(subscriptionRepository, org.mockito.Mockito.never()).saveAndFlush(any());
    verify(billingPeriodService, org.mockito.Mockito.never())
        .bindPlatformPeriodToSubscription(any(), any());
  }

  @Test
  void confirmedRenewalReplayReturnsSamePeriodWithoutSecondTransition() {
    Instant confirmedAt = Instant.parse("2026-11-01T00:01:00Z");
    CompanySubscription subscription = subscription(10L, SubscriptionStatus.ACTIVE);

    PlanPrice price = new PlanPrice();
    price.setId(21L);
    price.setPlan(subscription.getPlan());
    price.setBillingCycle(BillingCycle.MONTHLY);
    price.setAmount(new java.math.BigDecimal("120.00"));
    price.setCurrency("INR");
    price.setEffectiveFrom(Instant.parse("2026-01-01T00:00:00Z"));
    price.setStatus(RateCardStatus.ACTIVE);
    subscription.setPlanPrice(price);
    subscription.setCurrentPeriodStart(Instant.parse("2026-11-01T00:00:00Z"));
    subscription.setCurrentPeriodEnd(Instant.parse("2026-12-01T00:00:00Z"));

    BillingPeriod next = new BillingPeriod();
    next.setId(30L);
    next.setCompany(subscription.getCompany());
    next.setKind(BillingPeriodKind.PLATFORM);
    next.setPlan(subscription.getPlan());
    next.setPlanPrice(price);
    next.setPlatformAmountSnapshot(price.getAmount());
    next.setCurrencySnapshot("INR");
    next.setStartAt(subscription.getCurrentPeriodStart());
    next.setEndAt(subscription.getCurrentPeriodEnd());
    next.setSubscription(subscription);

    when(subscriptionRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(subscription));
    when(invoiceService.requirePaidCanonicalPlatformInvoice(1L, 900L)).thenReturn(next);

    CompanySubscription replay =
        service.renewAfterConfirmedCollection(10L, 900L, confirmedAt);

    assertEquals(subscription, replay);
    verify(subscriptionRepository, org.mockito.Mockito.never()).saveAndFlush(any());
    verify(transitionRepository, org.mockito.Mockito.never()).save(any());
  }


  @Test
  void pastDueSuspensionUsesClaimedRowsAndReplayConverges() {
    Instant now = Instant.parse("2026-10-10T00:00:00Z");
    CompanySubscription subscription = subscription(10L, SubscriptionStatus.PAST_DUE);
    when(subscriptionRepository.findPastDueForUpdate(now, 100))
        .thenReturn(List.of(subscription), List.of());
    when(subscriptionRepository.findExpiredTrialsForUpdate(now, 100))
        .thenReturn(List.of(), List.of());
    when(subscriptionRepository.findDueCancellationsForUpdate(now, 100))
        .thenReturn(List.of(), List.of());

    assertEquals(1, service.applyDueLifecycle(now));
    assertEquals(SubscriptionStatus.SUSPENDED, subscription.getStatus());
    assertEquals(0, service.applyDueLifecycle(now));

    verify(transitionRepository, times(1)).save(any(SubscriptionTransition.class));
  }

  @Test
  void recoveryIsAuditedAsSystemProviderTransition() {
    Instant now = Instant.parse("2026-10-10T00:00:00Z");
    CompanySubscription subscription = subscription(10L, SubscriptionStatus.SUSPENDED);
    when(subscriptionRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(subscription));
    when(subscriptionRepository.saveAndFlush(subscription)).thenReturn(subscription);

    service.recover(10L, now);

    assertEquals(SubscriptionStatus.ACTIVE, subscription.getStatus());
    ArgumentCaptor<SubscriptionTransition> captor =
        ArgumentCaptor.forClass(SubscriptionTransition.class);
    verify(transitionRepository).save(captor.capture());
    assertEquals(SubscriptionTransitionType.RECOVERED, captor.getValue().getTransitionType());
    assertEquals(null, captor.getValue().getActorUserId());
  }

  @Test
  void customerCancellationRetainsAuthenticatedActor() {
    Instant end = Instant.parse("2026-11-01T00:00:00Z");
    CompanySubscription subscription = subscription(10L, SubscriptionStatus.ACTIVE);
    subscription.setCurrentPeriodEnd(end);
    when(subscriptionRepository.findCurrentForUpdate(1L)).thenReturn(Optional.of(subscription));
    when(subscriptionRepository.saveAndFlush(subscription)).thenReturn(subscription);

    service.scheduleCancellation(1L, 77L);

    ArgumentCaptor<SubscriptionTransition> captor =
        ArgumentCaptor.forClass(SubscriptionTransition.class);
    verify(transitionRepository).save(captor.capture());
    assertEquals(77L, captor.getValue().getActorUserId());
    assertEquals(
        SubscriptionTransitionType.CANCELLATION_SCHEDULED,
        captor.getValue().getTransitionType());
    assertEquals(end, subscription.getCancellationEffectiveAt());
  }

  @Test
  void cancelAtPeriodEndExpiresOnlyAtBoundary() {
    Instant boundary = Instant.parse("2026-11-01T00:00:00Z");
    CompanySubscription subscription = subscription(10L, SubscriptionStatus.ACTIVE);
    subscription.setCancelAtPeriodEnd(true);
    subscription.setCurrentPeriodEnd(boundary);
    when(subscriptionRepository.findPastDueForUpdate(boundary, 100)).thenReturn(List.of());
    when(subscriptionRepository.findExpiredTrialsForUpdate(boundary, 100)).thenReturn(List.of());
    when(subscriptionRepository.findDueCancellationsForUpdate(boundary, 100))
        .thenReturn(List.of(subscription));

    assertEquals(1, service.applyDueLifecycle(boundary));
    assertEquals(SubscriptionStatus.EXPIRED, subscription.getStatus());
    assertEquals(boundary, subscription.getEndsAt());
  }

  private PlanPrice priced(Long id, Plan plan, String amount) {
    PlanPrice price = new PlanPrice();
    price.setId(id);
    price.setPlan(plan);
    price.setBillingCycle(BillingCycle.MONTHLY);
    price.setAmount(new java.math.BigDecimal(amount));
    price.setCurrency("INR");
    price.setEffectiveFrom(Instant.parse("2026-01-01T00:00:00Z"));
    price.setStatus(RateCardStatus.ACTIVE);
    return price;
  }

  private BillingPeriod paidPeriod(
      CompanySubscription subscription, Plan plan, PlanPrice price, Instant start) {
    BillingPeriod period = new BillingPeriod();
    period.setId(30L);
    period.setCompany(subscription.getCompany());
    period.setKind(BillingPeriodKind.PLATFORM);
    period.setCadence(BillingCycle.MONTHLY);
    period.setPlan(plan);
    period.setPlanPrice(price);
    period.setPlatformAmountSnapshot(price.getAmount());
    period.setCurrencySnapshot("INR");
    period.setStartAt(start);
    period.setEndAt(start.atZone(java.time.ZoneOffset.UTC).plusMonths(1).toInstant());
    period.setStatus(BillingPeriodStatus.OPEN);
    return period;
  }

  private CompanySubscription subscription(Long id, SubscriptionStatus status) {
    Company company = new Company();
    company.setId(1L);
    Plan plan = new Plan();
    plan.setId(2L);
    CompanySubscription subscription = new CompanySubscription();
    subscription.setId(id);
    subscription.setCompany(company);
    subscription.setPlan(plan);
    subscription.setStatus(status);
    subscription.setCurrentPeriodStart(Instant.parse("2026-10-01T00:00:00Z"));
    return subscription;
  }
}
