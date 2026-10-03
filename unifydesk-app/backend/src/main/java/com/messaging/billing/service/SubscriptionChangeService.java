package com.messaging.billing.service;

import com.messaging.billing.dto.SubscriptionChangeResponse;
import com.messaging.billing.dto.UpgradeProrationQuote;
import com.messaging.billing.entity.CompanySubscription;
import com.messaging.billing.entity.Plan;
import com.messaging.billing.entity.PlanPrice;
import com.messaging.billing.entity.SubscriptionChange;
import com.messaging.billing.enums.BillingCycle;
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

  private final SubscriptionChangeRepository changeRepository;
  private final PlanRepository planRepository;
  private final PlanPriceRepository planPriceRepository;
  private final PlanEligibilityService eligibilityService;
  private final SubscriptionLifecycleService lifecycleService;
  private final UpgradeProrationCalculator prorationCalculator;

  @Transactional
  public SubscriptionChangeResponse requestUpgrade(
      Long companyId,
      Long targetPlanId,
      Long targetPlanPriceId,
      String idempotencyKey,
      Long requestedByUserId,
      Instant requestedAt) {
    CompanySubscription current = lifecycleService.current(companyId);
    if (current.getStatus() != SubscriptionStatus.ACTIVE) {
      throw new ConflictException("Only an active subscription can be upgraded");
    }
    Plan target = requireTarget(companyId, targetPlanId, requestedAt);
    PlanPrice targetPrice = requirePrice(target, targetPlanPriceId);
    PlanPrice currentPrice = current.getPlanPrice();
    if (currentPrice == null) {
      throw new BadRequestException("Current subscription has no price snapshot");
    }
    validateIdempotencyKey(idempotencyKey);
    var prior = changeRepository.findByIdempotencyKey(idempotencyKey);
    if (prior.isPresent()) {
      assertSamePayload(prior.get(), current, target, SubscriptionChangeType.UPGRADE);
      return toResponse(prior.get());
    }

    UpgradeProrationQuote quote =
        prorationCalculator.quote(
            currentPrice.getAmount(),
            targetPrice.getAmount(),
            current.getCurrentPeriodStart(),
            current.getCurrentPeriodEnd(),
            requestedAt,
            "INR");

    SubscriptionChange change = baseChange(
        current, target, targetPrice, SubscriptionChangeType.UPGRADE, idempotencyKey,
        requestedByUserId, requestedAt, requestedAt);
    change.setStatus(
        quote.amountDue().signum() > 0
            ? SubscriptionChangeStatus.PENDING_PAYMENT
            : SubscriptionChangeStatus.SCHEDULED);
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
    return toResponse(changeRepository.saveAndFlush(saved));
  }

  @Transactional
  public SubscriptionChangeResponse scheduleDowngrade(
      Long companyId,
      Long targetPlanId,
      Long targetPlanPriceId,
      String idempotencyKey,
      Long requestedByUserId,
      Instant requestedAt) {
    CompanySubscription current = lifecycleService.current(companyId);
    if (current.getStatus() != SubscriptionStatus.ACTIVE) {
      throw new ConflictException("Only an active subscription can be downgraded");
    }
    if (current.getCurrentPeriodEnd() == null) {
      throw new BadRequestException("Downgrade requires a current billing-period boundary");
    }
    Plan target = requireTarget(companyId, targetPlanId, requestedAt);
    PlanPrice targetPrice = requirePrice(target, targetPlanPriceId);
    validateIdempotencyKey(idempotencyKey);
    var prior = changeRepository.findByIdempotencyKey(idempotencyKey);
    if (prior.isPresent()) {
      assertSamePayload(prior.get(), current, target, SubscriptionChangeType.DOWNGRADE);
      return toResponse(prior.get());
    }
    SubscriptionChange change =
        baseChange(
            current,
            target,
            targetPrice,
            SubscriptionChangeType.DOWNGRADE,
            idempotencyKey,
            requestedByUserId,
            requestedAt,
            current.getCurrentPeriodEnd());
    change.setStatus(SubscriptionChangeStatus.SCHEDULED);
    change.setCurrentPriceSnapshot(
        current.getPlanPrice() == null ? null : current.getPlanPrice().getAmount());
    change.setTargetPriceSnapshot(targetPrice.getAmount());
    change.setCurrentPeriodStart(current.getCurrentPeriodStart());
    change.setCurrentPeriodEnd(current.getCurrentPeriodEnd());
    change.setNetAmount(java.math.BigDecimal.ZERO);
    change.setAmountDue(java.math.BigDecimal.ZERO);
    change.setCreditAmount(java.math.BigDecimal.ZERO);
    SubscriptionChange saved = changeRepository.saveAndFlush(change);
    saved.setQuoteReference("DNG-" + saved.getId());
    return toResponse(changeRepository.saveAndFlush(saved));
  }

  /**
   * B2 calls this only after its provider/payment truth confirms the required upgrade amount.
   */
  @Transactional
  public SubscriptionChangeResponse applyConfirmedUpgrade(
      Long changeId, Long invoiceId, Instant confirmedAt) {
    SubscriptionChange change = require(changeId);
    if (change.getChangeType() != SubscriptionChangeType.UPGRADE) {
      throw new BadRequestException("Subscription change is not an upgrade");
    }
    if (change.getStatus() == SubscriptionChangeStatus.APPLIED) {
      return toResponse(change);
    }
    if (change.getStatus() != SubscriptionChangeStatus.PENDING_PAYMENT
        && change.getStatus() != SubscriptionChangeStatus.SCHEDULED) {
      throw new ConflictException("Upgrade is not applicable from its current state");
    }
    CompanySubscription subscription = change.getSubscription();
    if (subscription.getStatus() != SubscriptionStatus.ACTIVE) {
      throw new ConflictException("Subscription is not active");
    }
    Plan fromPlan = subscription.getPlan();
    subscription.setPlan(change.getToPlan());
    subscription.setPlanPrice(requirePrice(change.getToPlan(), change.getTargetPriceSnapshot()));
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
        changeRepository.findByStatusAndEffectiveAtLessThanEqualOrderByEffectiveAtAsc(
            SubscriptionChangeStatus.SCHEDULED, now);
    for (SubscriptionChange change : due) {
      if (change.getChangeType() != SubscriptionChangeType.DOWNGRADE) {
        continue;
      }
      CompanySubscription subscription = change.getSubscription();
      if (subscription.getStatus() != SubscriptionStatus.ACTIVE) {
        continue;
      }
      Plan fromPlan = subscription.getPlan();
      PlanPrice targetPrice = findPriceForPlanAndAmount(change.getToPlan(), change.getTargetPriceSnapshot());
      subscription.setPlan(change.getToPlan());
      subscription.setPlanPrice(targetPrice);
      subscription.setCurrentPeriodStart(change.getEffectiveAt());
      subscription.setBillingAnchor(change.getEffectiveAt());
      subscription.setCurrentPeriodEnd(nextBoundary(change.getEffectiveAt(), targetPrice.getBillingCycle()));
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

  private Plan requireTarget(Long companyId, Long planId, Instant at) {
    Plan plan =
        planRepository.findById(planId).orElseThrow(() -> new NotFoundException("Plan not found"));
    if (plan.getStatus() != PlanStatus.PUBLISHED) {
      throw new BadRequestException("Target plan is not published");
    }
    eligibilityService.assertCustomerEligible(companyId, plan, at);
    return plan;
  }

  private PlanPrice requirePrice(Plan plan, Long priceId) {
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
    return price;
  }

  private PlanPrice requirePrice(Plan plan, java.math.BigDecimal amount) {
    return findPriceForPlanAndAmount(plan, amount);
  }

  private PlanPrice findPriceForPlanAndAmount(Plan plan, java.math.BigDecimal amount) {
    return planPriceRepository
        .findByPlanIdAndStatusOrderByEffectiveFromDesc(plan.getId(), RateCardStatus.ACTIVE)
        .stream()
        .filter(price -> price.getAmount().compareTo(amount) == 0)
        .findFirst()
        .orElseThrow(() -> new NotFoundException("Target plan price snapshot is no longer resolvable"));
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
    change.setChangeType(type);
    change.setRequestedAt(requestedAt);
    change.setRequestedByUserId(actor);
    change.setEffectiveAt(effectiveAt);
    change.setIdempotencyKey(idempotencyKey.trim());
    change.setCurrency("INR");
    change.setTargetPriceSnapshot(targetPrice.getAmount());
    return change;
  }

  private void assertSamePayload(
      SubscriptionChange prior,
      CompanySubscription current,
      Plan target,
      SubscriptionChangeType type) {
    if (!prior.getCompany().getId().equals(current.getCompany().getId())
        || !prior.getSubscription().getId().equals(current.getId())
        || !prior.getToPlan().getId().equals(target.getId())
        || prior.getChangeType() != type) {
      throw new ConflictException("Idempotency key was already used for a different change");
    }
  }

  private void validateIdempotencyKey(String key) {
    if (key == null || key.isBlank() || key.length() > 255) {
      throw new BadRequestException("A valid idempotency key is required");
    }
  }

  private SubscriptionChange require(Long id) {
    return changeRepository
        .findById(id)
        .orElseThrow(() -> new NotFoundException("Subscription change not found"));
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
