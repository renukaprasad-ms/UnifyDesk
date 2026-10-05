package com.messaging.billing.service;

import com.messaging.billing.dto.SubscriptionChangeResponse;
import com.messaging.billing.dto.UpgradeProrationQuote;
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
import com.messaging.billing.enums.RateCardStatus;
import com.messaging.billing.enums.SubscriptionChangeStatus;
import com.messaging.billing.enums.SubscriptionChangeType;
import com.messaging.billing.enums.SubscriptionStatus;
import com.messaging.billing.enums.SubscriptionTransitionType;
import com.messaging.billing.repository.BillingPeriodRepository;
import com.messaging.billing.repository.PlanPriceRepository;
import com.messaging.billing.repository.PlanRepository;
import com.messaging.billing.repository.SubscriptionChangeRepository;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.ConflictException;
import com.messaging.common.exception.NotFoundException;
import com.messaging.company.repository.CompanyRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SubscriptionChangeService {
  private static final int CLAIM_BATCH_SIZE = 100;

  private final SubscriptionChangeRepository changeRepository;
  private final PlanRepository planRepository;
  private final PlanPriceRepository planPriceRepository;
  private final CompanyRepository companyRepository;
  private final PlanEligibilityService eligibilityService;
  private final SubscriptionLifecycleService lifecycleService;
  private final UpgradeProrationCalculator prorationCalculator;
  private final PlanChangeClassifier planChangeClassifier;
  private final BillingPeriodService billingPeriodService;
  private final BillingPeriodRepository billingPeriodRepository;
  private final UpgradeInvoiceService upgradeInvoiceService;

  @Transactional
  public SubscriptionChangeResponse requestUpgrade(
      Long companyId, Long targetPlanId, Long targetPlanPriceId, String idempotencyKey,
      Long requestedByUserId, Instant requestedAt) {
    lockCompany(companyId);
    String key = validateIdempotencyKey(idempotencyKey);
    var prior = changeRepository.findByCompanyIdAndIdempotencyKey(companyId, key);
    if (prior.isPresent()) {
      assertSameCommand(prior.get(), targetPlanId, targetPlanPriceId, SubscriptionChangeType.UPGRADE);
      return toResponse(prior.get());
    }
    CompanySubscription current = lifecycleService.currentForUpdate(companyId);
    if (current.getStatus() != SubscriptionStatus.ACTIVE) throw new ConflictException("Only an active subscription can be upgraded");
    PlanPrice currentPrice = current.getPlanPrice();
    if (currentPrice == null) throw new BadRequestException("Current subscription has no price snapshot");

    Plan target = requireTarget(companyId, targetPlanId, requestedAt);
    PlanPrice targetPrice = requirePrice(target, targetPlanPriceId, requestedAt);
    if (planChangeClassifier.classify(current.getPlan(), currentPrice, target, targetPrice) != PlanChangeDirection.UPGRADE) {
      throw new ConflictException("Selected target is not an authoritative upgrade; use the canonical change contract");
    }
    boolean openEndedFree = currentPrice.getBillingCycle() == BillingCycle.FREE
        && currentPrice.getAmount().compareTo(BigDecimal.ZERO) == 0
        && current.getCurrentPeriodEnd() == null;
    UpgradeProrationQuote quote = openEndedFree
        ? prorationCalculator.quoteFromFree(currentPrice.getAmount(), targetPrice.getAmount(), targetPrice.getBillingCycle(), requestedAt, "INR")
        : prorationCalculator.quote(currentPrice.getAmount(), targetPrice.getAmount(), targetPrice.getBillingCycle(), current.getCurrentPeriodStart(), current.getCurrentPeriodEnd(), requestedAt, "INR");

    SubscriptionChange change = baseChange(current, target, targetPrice, SubscriptionChangeType.UPGRADE, key, requestedByUserId, requestedAt, requestedAt);
    change.setStatus(SubscriptionChangeStatus.PENDING_PAYMENT);
    change.setCurrentPriceSnapshot(quote.currentPrice());
    change.setTargetPriceSnapshot(quote.targetPrice());
    change.setCurrentPeriodStart(quote.currentPeriodStart());
    change.setCurrentPeriodEnd(quote.currentPeriodEnd());
    change.setTargetProratedCharge(quote.targetProratedCharge());
    change.setUnusedCurrentCredit(quote.unusedCurrentCredit());
    change.setNetAmount(quote.netAmount());
    change.setAmountDue(quote.amountDue());
    change.setCreditAmount(quote.creditAmount());
    SubscriptionChange saved = changeRepository.saveAndFlush(change);
    saved.setQuoteReference("UPG-" + saved.getId());
    saved = changeRepository.saveAndFlush(saved);
    if (quote.amountDue().signum() == 0) return applyConfirmedUpgrade(saved.getId(), null, requestedAt);
    return toResponse(saved);
  }

  @Transactional
  public SubscriptionChangeResponse scheduleDowngrade(
      Long companyId, Long targetPlanId, Long targetPlanPriceId, String idempotencyKey,
      Long requestedByUserId, Instant requestedAt) {
    lockCompany(companyId);
    String key = validateIdempotencyKey(idempotencyKey);
    var prior = changeRepository.findByCompanyIdAndIdempotencyKey(companyId, key);
    if (prior.isPresent()) {
      assertSameCommand(prior.get(), targetPlanId, targetPlanPriceId, SubscriptionChangeType.DOWNGRADE);
      return toResponse(prior.get());
    }
    CompanySubscription current = lifecycleService.currentForUpdate(companyId);
    if (current.getStatus() != SubscriptionStatus.ACTIVE) throw new ConflictException("Only an active subscription can be downgraded");
    if (requestedAt == null || current.getCurrentPeriodEnd() == null) throw new BadRequestException("Downgrade requires a future current billing-period boundary");
    if (current.getPlanPrice() == null) throw new BadRequestException("Current subscription has no price snapshot");

    DowngradeScheduleSource schedule = resolveDowngradeScheduleSource(current, requestedAt);
    Plan target = requireTarget(companyId, targetPlanId, requestedAt);
    PlanPrice targetPrice = requirePrice(target, targetPlanPriceId, schedule.effectiveAt());
    if (planChangeClassifier.classify(schedule.fromPlan(), schedule.fromPlanPrice(), target, targetPrice) != PlanChangeDirection.DOWNGRADE) {
      throw new ConflictException("Selected target is not an authoritative downgrade; use the canonical change contract");
    }
    assertNoAuthoritativeScheduledDowngrade(current.getId(), schedule.effectiveAt());
    SubscriptionChange change = baseChange(current, target, targetPrice, SubscriptionChangeType.DOWNGRADE, key, requestedByUserId, requestedAt, schedule.effectiveAt());
    change.setFromPlan(schedule.fromPlan());
    change.setFromPlanPrice(schedule.fromPlanPrice());
    change.setStatus(SubscriptionChangeStatus.SCHEDULED);
    change.setCurrentPriceSnapshot(schedule.fromPlanPrice().getAmount());
    change.setTargetPriceSnapshot(targetPrice.getAmount());
    change.setCurrentPeriodStart(schedule.periodStart());
    change.setCurrentPeriodEnd(schedule.periodEnd());
    change.setNetAmount(BigDecimal.ZERO);
    change.setAmountDue(BigDecimal.ZERO);
    change.setCreditAmount(BigDecimal.ZERO);
    try {
      SubscriptionChange saved = changeRepository.saveAndFlush(change);
      saved.setQuoteReference("DNG-" + saved.getId());
      return toResponse(changeRepository.saveAndFlush(saved));
    } catch (DataIntegrityViolationException exception) {
      throw new ConflictException(
          "A scheduled downgrade already owns this subscription billing boundary");
    }
  }

  @Transactional
  public SubscriptionChangeResponse applyConfirmedUpgrade(Long changeId, Long invoiceId, Instant confirmedAt) {
    SubscriptionChange change = changeRepository.findByIdForUpdate(changeId).orElseThrow(() -> new NotFoundException("Subscription change not found"));
    if (change.getChangeType() != SubscriptionChangeType.UPGRADE) throw new BadRequestException("Subscription change is not an upgrade");
    if (change.getStatus() == SubscriptionChangeStatus.APPLIED) return toResponse(change);
    if (change.getStatus() != SubscriptionChangeStatus.PENDING_PAYMENT) throw new ConflictException("Upgrade is not awaiting payment confirmation");
    if (confirmedAt == null) throw new BadRequestException("Confirmed upgrade time is required");
    if (change.getAmountDue() != null && change.getAmountDue().signum() > 0) {
      if (invoiceId == null) throw new ConflictException("A paid upgrade cannot be applied without confirmed financial evidence");
      upgradeInvoiceService.assertPaidCanonicalInvoice(change, invoiceId);
    }
    CompanySubscription subscription = lifecycleService.requireForUpdate(change.getSubscription().getId());
    if (subscription.getStatus() != SubscriptionStatus.ACTIVE) throw new ConflictException("Subscription is not active");
    assertSubscriptionStillMatches(change, subscription);

    boolean fromOpenEndedFree = change.getFromPlanPrice() != null
        && change.getFromPlanPrice().getBillingCycle() == BillingCycle.FREE
        && change.getFromPlanPrice().getAmount().compareTo(BigDecimal.ZERO) == 0
        && subscription.getCurrentPeriodEnd() == null;
    Plan fromPlan = subscription.getPlan();
    if (fromOpenEndedFree) {
      PlanPrice targetPrice = change.getToPlanPrice();
      Instant paidStart = change.getCurrentPeriodStart();
      Instant paidEnd = change.getCurrentPeriodEnd();
      assertCanonicalFreeToPaidPeriod(change, targetPrice, paidStart, paidEnd);
      BillingPeriod targetPeriod = billingPeriodService.ensurePlatformPeriodSnapshot(subscription.getCompany(), change.getToPlan(), targetPrice, paidStart, paidEnd);
      subscription.setPlan(change.getToPlan());
      subscription.setPlanPrice(targetPrice);
      subscription.setCurrentPeriodStart(paidStart);
      subscription.setCurrentPeriodEnd(paidEnd);
      subscription.setBillingAnchor(paidStart);
      billingPeriodService.bindPlatformPeriodToSubscription(targetPeriod.getId(), subscription);
    } else {
      billingPeriodService.ensurePlatformPeriod(subscription);
      subscription.setPlan(change.getToPlan());
      subscription.setPlanPrice(change.getToPlanPrice());
    }
    lifecycleService.recordPlanChange(subscription, fromPlan, change.getToPlan(), SubscriptionTransitionType.UPGRADED, change.getRequestedByUserId(), fromOpenEndedFree ? "Provider-confirmed FREE to paid upgrade applied" : "Provider-confirmed upgrade applied", confirmedAt);
    change.setInvoiceId(invoiceId);
    change.setAppliedAt(confirmedAt);
    change.setStatus(SubscriptionChangeStatus.APPLIED);
    return toResponse(changeRepository.saveAndFlush(change));
  }

  @Transactional
  public int applyDueDowngrades(Instant now) {
    int count = 0;
    for (SubscriptionChange change : changeRepository.findDueDowngradesForUpdate(now, CLAIM_BATCH_SIZE)) {
      CompanySubscription subscription = lifecycleService.requireForUpdate(change.getSubscription().getId());
      if (subscription.getStatus() != SubscriptionStatus.ACTIVE || !downgradeBoundaryMatches(change, subscription)) {
        change.setStatus(SubscriptionChangeStatus.FAILED);
        changeRepository.save(change);
        continue;
      }
      if (subscription.getPaymentPolicy() == PaymentPolicy.CASHFREE) continue;
      Plan fromPlan = subscription.getPlan();
      PlanPrice targetPrice = change.getToPlanPrice();
      subscription.setPlan(change.getToPlan());
      subscription.setPlanPrice(targetPrice);
      subscription.setCurrentPeriodStart(change.getEffectiveAt());
      subscription.setBillingAnchor(change.getEffectiveAt());
      subscription.setCurrentPeriodEnd(nextBoundary(change.getEffectiveAt(), targetPrice.getBillingCycle()));
      billingPeriodService.ensurePlatformPeriod(subscription);
      change.setStatus(SubscriptionChangeStatus.APPLIED);
      change.setAppliedAt(now);
      lifecycleService.recordPlanChange(subscription, fromPlan, change.getToPlan(), SubscriptionTransitionType.DOWNGRADED, change.getRequestedByUserId(), "Scheduled downgrade reached billing boundary", change.getEffectiveAt());
      changeRepository.save(change);
      count++;
    }
    changeRepository.flush();
    return count;
  }

  private DowngradeScheduleSource resolveDowngradeScheduleSource(CompanySubscription current, Instant requestedAt) {
    Instant boundary = current.getCurrentPeriodEnd();
    if (current.getPaymentPolicy() != PaymentPolicy.CASHFREE) {
      if (!requestedAt.isBefore(boundary)) throw new ConflictException("Downgrade request must precede its effective boundary");
      return new DowngradeScheduleSource(current.getPlan(), current.getPlanPrice(), current.getCurrentPeriodStart(), boundary, boundary);
    }
    List<BillingPeriod> owned = billingPeriodRepository.findByCompanyIdAndKindAndStartAt(current.getCompany().getId(), BillingPeriodKind.PLATFORM, boundary);
    if (owned.size() > 1) throw new ConflictException("Multiple PLATFORM periods claim the subscription renewal boundary");
    if (owned.isEmpty()) {
      if (!requestedAt.isBefore(boundary)) throw new ConflictException("Downgrade request must precede its effective boundary");
      return new DowngradeScheduleSource(current.getPlan(), current.getPlanPrice(), current.getCurrentPeriodStart(), boundary, boundary);
    }
    BillingPeriod renewalOwner = owned.getFirst();
    if (renewalOwner.getPlan() == null || renewalOwner.getPlanPrice() == null || renewalOwner.getPlatformAmountSnapshot() == null || renewalOwner.getEndAt() == null || !renewalOwner.getEndAt().isAfter(renewalOwner.getStartAt())) {
      throw new ConflictException("Owned renewal boundary has no safe next commercial boundary for a downgrade");
    }
    if (!requestedAt.isBefore(renewalOwner.getEndAt())) throw new ConflictException("Downgrade request must precede the next valid future boundary");
    if (renewalOwner.getPlanPrice().getPlan() == null || !renewalOwner.getPlanPrice().getPlan().getId().equals(renewalOwner.getPlan().getId()) || renewalOwner.getPlatformAmountSnapshot().compareTo(renewalOwner.getPlanPrice().getAmount()) != 0) {
      throw new ConflictException("Owned renewal boundary has inconsistent commercial truth");
    }
    return new DowngradeScheduleSource(renewalOwner.getPlan(), renewalOwner.getPlanPrice(), renewalOwner.getStartAt(), renewalOwner.getEndAt(), renewalOwner.getEndAt());
  }

  private void assertNoAuthoritativeScheduledDowngrade(Long subscriptionId, Instant effectiveAt) {
    if (changeRepository
        .findBySubscriptionIdAndChangeTypeAndStatusAndEffectiveAt(
            subscriptionId,
            SubscriptionChangeType.DOWNGRADE,
            SubscriptionChangeStatus.SCHEDULED,
            effectiveAt)
        .isPresent()) {
      throw new ConflictException(
          "A scheduled downgrade already owns this subscription billing boundary");
    }
  }

  private void assertCanonicalFreeToPaidPeriod(
      SubscriptionChange change, PlanPrice targetPrice, Instant paidStart, Instant paidEnd) {
    if (paidStart == null || paidEnd == null || !paidEnd.isAfter(paidStart)) {
      throw new ConflictException("FREE upgrade has an invalid immutable paid service period");
    }
    BillingCycle cycle = targetPrice == null ? null : targetPrice.getBillingCycle();
    if ((cycle != BillingCycle.MONTHLY && cycle != BillingCycle.YEARLY)
        || targetPrice.getAmount() == null
        || targetPrice.getAmount().signum() <= 0) {
      throw new ConflictException("FREE upgrade activation requires a positive MONTHLY or YEARLY target");
    }
    if (change.getToPlan() == null
        || targetPrice.getPlan() == null
        || !targetPrice.getPlan().getId().equals(change.getToPlan().getId())
        || targetPrice.getStatus() != RateCardStatus.ACTIVE
        || !"INR".equalsIgnoreCase(targetPrice.getCurrency())
        || !"INR".equalsIgnoreCase(change.getCurrency())
        || change.getTargetPriceSnapshot() == null
        || change.getTargetPriceSnapshot().compareTo(targetPrice.getAmount()) != 0) {
      throw new ConflictException("FREE upgrade target financial snapshot is inconsistent");
    }
    FinancialEffectiveTime.assertStoredActiveRange(targetPrice.getEffectiveFrom(), targetPrice.getEffectiveTo(), "PlanPrice");
    if (!FinancialEffectiveTime.includes(targetPrice.getEffectiveFrom(), targetPrice.getEffectiveTo(), paidStart)) {
      throw new ConflictException("FREE upgrade target price is not effective at the immutable paid service-period start");
    }
    Instant expectedEnd = nextBoundary(paidStart, cycle);
    if (!paidEnd.equals(expectedEnd)) {
      throw new ConflictException("FREE upgrade immutable paid service period does not match target cadence");
    }
  }

  private void lockCompany(Long companyId) { companyRepository.findByIdForUpdate(companyId).orElseThrow(() -> new NotFoundException("Company not found")); }

  private Plan requireTarget(Long companyId, Long planId, Instant at) {
    Plan plan = planRepository.findById(planId).orElseThrow(() -> new NotFoundException("Plan not found"));
    if (plan.getStatus() != PlanStatus.PUBLISHED) throw new BadRequestException("Target plan is not published");
    eligibilityService.assertCustomerEligible(companyId, plan, at);
    return plan;
  }

  private PlanPrice requirePrice(Plan plan, Long priceId, Instant pricingAt) {
    PlanPrice price = planPriceRepository.findById(priceId).orElseThrow(() -> new NotFoundException("Plan price not found"));
    if (!price.getPlan().getId().equals(plan.getId()) || price.getStatus() != RateCardStatus.ACTIVE) throw new BadRequestException("Plan price does not belong to the selected plan");
    if (!"INR".equalsIgnoreCase(price.getCurrency())) throw new BadRequestException("Billing R1 supports INR only");
    FinancialEffectiveTime.assertStoredActiveRange(price.getEffectiveFrom(), price.getEffectiveTo(), "PlanPrice");
    if (!FinancialEffectiveTime.includes(price.getEffectiveFrom(), price.getEffectiveTo(), pricingAt)) throw new BadRequestException("Plan price is not effective at the canonical subscription-change pricing time");
    return price;
  }

  private SubscriptionChange baseChange(CompanySubscription current, Plan target, PlanPrice targetPrice, SubscriptionChangeType type, String idempotencyKey, Long actor, Instant requestedAt, Instant effectiveAt) {
    SubscriptionChange change = new SubscriptionChange();
    change.setCompany(current.getCompany());
    change.setSubscription(current);
    change.setFromPlan(current.getPlan());
    change.setToPlan(target);
    change.setFromPlanPrice(current.getPlanPrice());
    change.setToPlanPrice(targetPrice);
    change.setChangeType(type);
    change.setRequestedAt(requestedAt);
    change.setRequestedByUserId(actor);
    change.setEffectiveAt(effectiveAt);
    change.setIdempotencyKey(idempotencyKey);
    change.setCurrency("INR");
    change.setTargetPriceSnapshot(targetPrice.getAmount());
    return change;
  }

  private void assertSameCommand(SubscriptionChange prior, Long targetPlanId, Long targetPlanPriceId, SubscriptionChangeType type) {
    if (!prior.getToPlan().getId().equals(targetPlanId) || !prior.getToPlanPrice().getId().equals(targetPlanPriceId) || prior.getChangeType() != type) throw new ConflictException("Idempotency key was already used for a materially different subscription change");
  }

  private void assertSubscriptionStillMatches(SubscriptionChange change, CompanySubscription subscription) {
    if (!subscriptionMatches(change, subscription)) throw new ConflictException("Subscription changed after quote creation; upgrade cannot be applied");
  }

  private boolean subscriptionMatches(SubscriptionChange change, CompanySubscription subscription) {
    if (!subscription.getPlan().getId().equals(change.getFromPlan().getId())) return false;
    Long expectedPriceId = change.getFromPlanPrice() == null ? null : change.getFromPlanPrice().getId();
    Long actualPriceId = subscription.getPlanPrice() == null ? null : subscription.getPlanPrice().getId();
    return java.util.Objects.equals(expectedPriceId, actualPriceId);
  }

  private boolean downgradeBoundaryMatches(SubscriptionChange change, CompanySubscription subscription) {
    return subscriptionMatches(change, subscription)
        && java.util.Objects.equals(change.getCurrentPeriodStart(), subscription.getCurrentPeriodStart())
        && java.util.Objects.equals(change.getCurrentPeriodEnd(), subscription.getCurrentPeriodEnd())
        && java.util.Objects.equals(change.getEffectiveAt(), subscription.getCurrentPeriodEnd());
  }

  private String validateIdempotencyKey(String key) {
    if (key == null || key.isBlank() || key.length() > 255) throw new BadRequestException("A valid idempotency key is required");
    return key.trim();
  }

  private Instant nextBoundary(Instant start, BillingCycle cycle) {
    ZonedDateTime utc = start.atZone(ZoneOffset.UTC);
    return switch (cycle) { case MONTHLY -> utc.plusMonths(1).toInstant(); case YEARLY -> utc.plusYears(1).toInstant(); case FREE -> null; };
  }

  private record DowngradeScheduleSource(Plan fromPlan, PlanPrice fromPlanPrice, Instant periodStart, Instant periodEnd, Instant effectiveAt) {}

  private SubscriptionChangeResponse toResponse(SubscriptionChange change) {
    return new SubscriptionChangeResponse(change.getId().toString(), change.getCompany().getId().toString(), change.getSubscription().getId().toString(), change.getFromPlan().getId().toString(), change.getToPlan().getId().toString(), change.getChangeType(), change.getStatus(), change.getRequestedAt(), change.getEffectiveAt(), change.getQuoteReference(), change.getInvoiceId() == null ? null : change.getInvoiceId().toString(), change.getCurrency(), change.getCurrentPriceSnapshot(), change.getTargetPriceSnapshot(), change.getTargetProratedCharge(), change.getUnusedCurrentCredit(), change.getNetAmount(), change.getAmountDue(), change.getCreditAmount());
  }
}
