package com.messaging.billing.service;

import com.messaging.billing.dto.SubscriptionChangeResponse;
import com.messaging.billing.dto.UpgradeProrationQuote;
import com.messaging.billing.entity.CompanySubscription;
import com.messaging.billing.entity.Plan;
import com.messaging.billing.entity.PlanPrice;
import com.messaging.billing.entity.SubscriptionChange;
import com.messaging.billing.enums.BillingCycle;
import com.messaging.billing.enums.PaymentPolicy;
import com.messaging.billing.enums.PlanChangeDirection;
import com.messaging.billing.enums.PlanStatus;
import com.messaging.billing.enums.RateCardStatus;
import com.messaging.billing.enums.SubscriptionChangeStatus;
import com.messaging.billing.enums.SubscriptionChangeType;
import com.messaging.billing.enums.SubscriptionStatus;
import com.messaging.billing.enums.SubscriptionTransitionType;
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
  private final UpgradeInvoiceService upgradeInvoiceService;

  @Transactional
  public SubscriptionChangeResponse requestUpgrade(
      Long companyId,
      Long targetPlanId,
      Long targetPlanPriceId,
      String idempotencyKey,
      Long requestedByUserId,
      Instant requestedAt) {
    lockCompany(companyId);
    String key = validateIdempotencyKey(idempotencyKey);
    var prior = changeRepository.findByCompanyIdAndIdempotencyKey(companyId, key);
    if (prior.isPresent()) {
      assertSameCommand(
          prior.get(), targetPlanId, targetPlanPriceId, SubscriptionChangeType.UPGRADE);
      return toResponse(prior.get());
    }

    CompanySubscription current = lifecycleService.currentForUpdate(companyId);
    if (current.getStatus() != SubscriptionStatus.ACTIVE) {
      throw new ConflictException("Only an active subscription can be upgraded");
    }
    PlanPrice currentPrice = current.getPlanPrice();
    if (currentPrice == null) {
      throw new BadRequestException("Current subscription has no price snapshot");
    }

    Plan target = requireTarget(companyId, targetPlanId, requestedAt);
    PlanPrice targetPrice = requirePrice(target, targetPlanPriceId, requestedAt);
    PlanChangeDirection direction =
        planChangeClassifier.classify(current.getPlan(), currentPrice, target, targetPrice);
    if (direction != PlanChangeDirection.UPGRADE) {
      throw new ConflictException(
          "Selected target is not an authoritative upgrade; use the canonical change contract");
    }

    UpgradeProrationQuote quote =
        prorationCalculator.quote(
            currentPrice.getAmount(),
            targetPrice.getAmount(),
            targetPrice.getBillingCycle(),
            current.getCurrentPeriodStart(),
            current.getCurrentPeriodEnd(),
            requestedAt,
            "INR");

    SubscriptionChange change =
        baseChange(
            current,
            target,
            targetPrice,
            SubscriptionChangeType.UPGRADE,
            key,
            requestedByUserId,
            requestedAt,
            requestedAt);
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

    if (quote.amountDue().signum() == 0) {
      return applyConfirmedUpgrade(saved.getId(), null, requestedAt);
    }
    return toResponse(saved);
  }

  @Transactional
  public SubscriptionChangeResponse scheduleDowngrade(
      Long companyId,
      Long targetPlanId,
      Long targetPlanPriceId,
      String idempotencyKey,
      Long requestedByUserId,
      Instant requestedAt) {
    lockCompany(companyId);
    String key = validateIdempotencyKey(idempotencyKey);
    var prior = changeRepository.findByCompanyIdAndIdempotencyKey(companyId, key);
    if (prior.isPresent()) {
      assertSameCommand(
          prior.get(), targetPlanId, targetPlanPriceId, SubscriptionChangeType.DOWNGRADE);
      return toResponse(prior.get());
    }

    CompanySubscription current = lifecycleService.currentForUpdate(companyId);
    if (current.getStatus() != SubscriptionStatus.ACTIVE) {
      throw new ConflictException("Only an active subscription can be downgraded");
    }
    if (current.getCurrentPeriodEnd() == null) {
      throw new BadRequestException("Downgrade requires a current billing-period boundary");
    }
    PlanPrice currentPrice = current.getPlanPrice();
    if (currentPrice == null) {
      throw new BadRequestException("Current subscription has no price snapshot");
    }

    Plan target = requireTarget(companyId, targetPlanId, requestedAt);
    // Downgrade pricing is frozen for the time the target plan actually becomes effective.
    PlanPrice targetPrice =
        requirePrice(target, targetPlanPriceId, current.getCurrentPeriodEnd());
    PlanChangeDirection direction =
        planChangeClassifier.classify(current.getPlan(), currentPrice, target, targetPrice);
    if (direction != PlanChangeDirection.DOWNGRADE) {
      throw new ConflictException(
          "Selected target is not an authoritative downgrade; use the canonical change contract");
    }

    SubscriptionChange change =
        baseChange(
            current,
            target,
            targetPrice,
            SubscriptionChangeType.DOWNGRADE,
            key,
            requestedByUserId,
            requestedAt,
            current.getCurrentPeriodEnd());
    change.setStatus(SubscriptionChangeStatus.SCHEDULED);
    change.setCurrentPriceSnapshot(currentPrice.getAmount());
    change.setTargetPriceSnapshot(targetPrice.getAmount());
    change.setCurrentPeriodStart(current.getCurrentPeriodStart());
    change.setCurrentPeriodEnd(current.getCurrentPeriodEnd());
    change.setNetAmount(BigDecimal.ZERO);
    change.setAmountDue(BigDecimal.ZERO);
    change.setCreditAmount(BigDecimal.ZERO);

    SubscriptionChange saved = changeRepository.saveAndFlush(change);
    saved.setQuoteReference("DNG-" + saved.getId());
    return toResponse(changeRepository.saveAndFlush(saved));
  }

  /**
   * B2 calls this only after provider/payment truth confirms the required upgrade amount.
   */
  @Transactional
  public SubscriptionChangeResponse applyConfirmedUpgrade(
      Long changeId, Long invoiceId, Instant confirmedAt) {
    SubscriptionChange change =
        changeRepository
            .findByIdForUpdate(changeId)
            .orElseThrow(() -> new NotFoundException("Subscription change not found"));
    if (change.getChangeType() != SubscriptionChangeType.UPGRADE) {
      throw new BadRequestException("Subscription change is not an upgrade");
    }
    if (change.getStatus() == SubscriptionChangeStatus.APPLIED) {
      return toResponse(change);
    }
    if (change.getStatus() != SubscriptionChangeStatus.PENDING_PAYMENT) {
      throw new ConflictException("Upgrade is not awaiting payment confirmation");
    }
    if (change.getAmountDue() != null && change.getAmountDue().signum() > 0) {
      if (invoiceId == null) {
        throw new ConflictException(
            "A paid upgrade cannot be applied without confirmed financial evidence");
      }
      upgradeInvoiceService.assertPaidCanonicalInvoice(change, invoiceId);
    }

    CompanySubscription subscription =
        lifecycleService.requireForUpdate(change.getSubscription().getId());
    if (subscription.getStatus() != SubscriptionStatus.ACTIVE) {
      throw new ConflictException("Subscription is not active");
    }
    assertSubscriptionStillMatches(change, subscription);

    // Freeze the pre-upgrade platform-period snapshot before changing the commercial plan.
    billingPeriodService.ensurePlatformPeriod(subscription);

    Plan fromPlan = subscription.getPlan();
    subscription.setPlan(change.getToPlan());
    subscription.setPlanPrice(change.getToPlanPrice());
    lifecycleService.recordPlanChange(
        subscription,
        fromPlan,
        change.getToPlan(),
        SubscriptionTransitionType.UPGRADED,
        change.getRequestedByUserId(),
        "Provider-confirmed upgrade applied",
        confirmedAt);

    change.setInvoiceId(invoiceId);
    change.setAppliedAt(confirmedAt);
    change.setStatus(SubscriptionChangeStatus.APPLIED);
    return toResponse(changeRepository.saveAndFlush(change));
  }

  @Transactional
  public int applyDueDowngrades(Instant now) {
    int count = 0;
    List<SubscriptionChange> due =
        changeRepository.findDueDowngradesForUpdate(now, CLAIM_BATCH_SIZE);
    for (SubscriptionChange change : due) {
      CompanySubscription subscription =
          lifecycleService.requireForUpdate(change.getSubscription().getId());
      if (subscription.getStatus() != SubscriptionStatus.ACTIVE
          || !downgradeBoundaryMatches(change, subscription)) {
        change.setStatus(SubscriptionChangeStatus.FAILED);
        changeRepository.save(change);
        continue;
      }
      if (subscription.getPaymentPolicy() == PaymentPolicy.CASHFREE) {
        continue;
      }

      Plan fromPlan = subscription.getPlan();
      PlanPrice targetPrice = change.getToPlanPrice();
      subscription.setPlan(change.getToPlan());
      subscription.setPlanPrice(targetPrice);
      subscription.setCurrentPeriodStart(change.getEffectiveAt());
      subscription.setBillingAnchor(change.getEffectiveAt());
      subscription.setCurrentPeriodEnd(
          nextBoundary(change.getEffectiveAt(), targetPrice.getBillingCycle()));
      billingPeriodService.ensurePlatformPeriod(subscription);

      change.setStatus(SubscriptionChangeStatus.APPLIED);
      change.setAppliedAt(now);
      lifecycleService.recordPlanChange(
          subscription,
          fromPlan,
          change.getToPlan(),
          SubscriptionTransitionType.DOWNGRADED,
          change.getRequestedByUserId(),
          "Scheduled downgrade reached billing boundary",
          change.getEffectiveAt());
      changeRepository.save(change);
      count++;
    }
    changeRepository.flush();
    return count;
  }

  private void lockCompany(Long companyId) {
    companyRepository
        .findByIdForUpdate(companyId)
        .orElseThrow(() -> new NotFoundException("Company not found"));
  }

  private Plan requireTarget(Long companyId, Long planId, Instant at) {
    Plan plan =
        planRepository.findById(planId).orElseThrow(() -> new NotFoundException("Plan not found"));
    if (plan.getStatus() != PlanStatus.PUBLISHED) {
      throw new BadRequestException("Target plan is not published");
    }
    eligibilityService.assertCustomerEligible(companyId, plan, at);
    return plan;
  }

  private PlanPrice requirePrice(Plan plan, Long priceId, Instant pricingAt) {
    PlanPrice price =
        planPriceRepository
            .findById(priceId)
            .orElseThrow(() -> new NotFoundException("Plan price not found"));
    if (!price.getPlan().getId().equals(plan.getId()) || price.getStatus() != RateCardStatus.ACTIVE) {
      throw new BadRequestException("Plan price does not belong to the selected plan");
    }
    if (!"INR".equalsIgnoreCase(price.getCurrency())) {
      throw new BadRequestException("Billing R1 supports INR only");
    }
    if (pricingAt == null
        || price.getEffectiveFrom() == null
        || price.getEffectiveFrom().isAfter(pricingAt)
        || (price.getEffectiveTo() != null && !pricingAt.isBefore(price.getEffectiveTo()))) {
      throw new BadRequestException(
          "Plan price is not effective at the canonical subscription-change pricing time");
    }
    return price;
  }

  private SubscriptionChange baseChange(
      CompanySubscription current,
      Plan target,
      PlanPrice targetPrice,
      SubscriptionChangeType type,
      String idempotencyKey,
      Long actor,
      Instant requestedAt,
      Instant effectiveAt) {
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

  private void assertSameCommand(
      SubscriptionChange prior,
      Long targetPlanId,
      Long targetPlanPriceId,
      SubscriptionChangeType type) {
    if (!prior.getToPlan().getId().equals(targetPlanId)
        || !prior.getToPlanPrice().getId().equals(targetPlanPriceId)
        || prior.getChangeType() != type) {
      throw new ConflictException(
          "Idempotency key was already used for a materially different subscription change");
    }
  }

  private void assertSubscriptionStillMatches(
      SubscriptionChange change, CompanySubscription subscription) {
    if (!subscriptionMatches(change, subscription)) {
      throw new ConflictException(
          "Subscription changed after quote creation; upgrade cannot be applied");
    }
  }

  private boolean subscriptionMatches(
      SubscriptionChange change, CompanySubscription subscription) {
    if (!subscription.getPlan().getId().equals(change.getFromPlan().getId())) {
      return false;
    }
    Long expectedPriceId =
        change.getFromPlanPrice() == null ? null : change.getFromPlanPrice().getId();
    Long actualPriceId =
        subscription.getPlanPrice() == null ? null : subscription.getPlanPrice().getId();
    return java.util.Objects.equals(expectedPriceId, actualPriceId);
  }

  private boolean downgradeBoundaryMatches(
      SubscriptionChange change, CompanySubscription subscription) {
    return subscriptionMatches(change, subscription)
        && java.util.Objects.equals(
            change.getCurrentPeriodStart(), subscription.getCurrentPeriodStart())
        && java.util.Objects.equals(
            change.getCurrentPeriodEnd(), subscription.getCurrentPeriodEnd())
        && java.util.Objects.equals(change.getEffectiveAt(), subscription.getCurrentPeriodEnd());
  }

  private String validateIdempotencyKey(String key) {
    if (key == null || key.isBlank() || key.length() > 255) {
      throw new BadRequestException("A valid idempotency key is required");
    }
    return key.trim();
  }

  private Instant nextBoundary(Instant start, BillingCycle cycle) {
    ZonedDateTime utc = start.atZone(ZoneOffset.UTC);
    return switch (cycle) {
      case MONTHLY -> utc.plusMonths(1).toInstant();
      case YEARLY -> utc.plusYears(1).toInstant();
      case FREE -> null;
    };
  }

  private SubscriptionChangeResponse toResponse(SubscriptionChange change) {
    return new SubscriptionChangeResponse(
        change.getId().toString(),
        change.getCompany().getId().toString(),
        change.getSubscription().getId().toString(),
        change.getFromPlan().getId().toString(),
        change.getToPlan().getId().toString(),
        change.getChangeType(),
        change.getStatus(),
        change.getRequestedAt(),
        change.getEffectiveAt(),
        change.getQuoteReference(),
        change.getInvoiceId() == null ? null : change.getInvoiceId().toString(),
        change.getCurrency(),
        change.getCurrentPriceSnapshot(),
        change.getTargetPriceSnapshot(),
        change.getTargetProratedCharge(),
        change.getUnusedCurrentCredit(),
        change.getNetAmount(),
        change.getAmountDue(),
        change.getCreditAmount());
  }
}
