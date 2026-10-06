package com.messaging.billing.service;

import com.messaging.billing.entity.CompanySubscription;
import com.messaging.billing.entity.Plan;
import com.messaging.billing.entity.PlanPrice;
import com.messaging.billing.entity.SubscriptionTransition;
import com.messaging.billing.enums.PaymentPolicy;
import com.messaging.billing.enums.SubscriptionStatus;
import com.messaging.billing.enums.SubscriptionTransitionType;
import com.messaging.billing.repository.CompanySubscriptionRepository;
import com.messaging.billing.repository.SubscriptionTransitionRepository;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.ConflictException;
import com.messaging.common.exception.NotFoundException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SubscriptionLifecycleService {

  public static final Duration PAST_DUE_GRACE = Duration.ofDays(7);
  private static final int CLAIM_BATCH_SIZE = 100;

  private static final List<SubscriptionStatus> CURRENT_STATUSES =
      List.of(
          SubscriptionStatus.ACTIVE,
          SubscriptionStatus.TRIALING,
          SubscriptionStatus.PAST_DUE,
          SubscriptionStatus.SUSPENDED);

  private final CompanySubscriptionRepository subscriptionRepository;
  private final SubscriptionTransitionRepository transitionRepository;
  private final BillingPeriodService billingPeriodService;
  private final InvoiceService invoiceService;
  private final SubscriptionBoundaryChangeService boundaryChangeService;

  @Transactional(readOnly = true)
  public CompanySubscription current(Long companyId) {
    return currentOptional(companyId)
        .orElseThrow(() -> new NotFoundException("Company subscription not found"));
  }

  @Transactional(readOnly = true)
  public Optional<CompanySubscription> currentOptional(Long companyId) {
    return subscriptionRepository.findFirstByCompanyIdAndStatusInOrderByStartedAtDesc(
        companyId, CURRENT_STATUSES);
  }

  @Transactional
  public CompanySubscription currentForUpdate(Long companyId) {
    return subscriptionRepository
        .findCurrentForUpdate(companyId)
        .orElseThrow(() -> new NotFoundException("Company subscription not found"));
  }

  @Transactional(readOnly = true)
  public CompanySubscription require(Long subscriptionId) {
    return subscriptionRepository
        .findById(subscriptionId)
        .orElseThrow(() -> new NotFoundException("Company subscription not found"));
  }

  @Transactional
  public CompanySubscription requireForUpdate(Long subscriptionId) {
    return subscriptionRepository
        .findByIdForUpdate(subscriptionId)
        .orElseThrow(() -> new NotFoundException("Company subscription not found"));
  }

  @Transactional
  public void recordCreated(
      CompanySubscription subscription,
      Long actorUserId,
      String reason,
      SubscriptionTransitionType type) {
    ensureCurrentPlatformPeriodIfPresent(subscription);
    record(
        subscription,
        null,
        subscription.getStatus(),
        null,
        subscription.getPlan(),
        type,
        actorUserId,
        reason,
        subscription.getPaymentPolicy(),
        subscription.getExternalContractReference(),
        subscription.getStartedAt());
  }

  @Transactional
  public CompanySubscription scheduleCancellation(Long companyId, Long actorUserId) {
    CompanySubscription subscription = currentForUpdate(companyId);
    if (subscription.getStatus() != SubscriptionStatus.ACTIVE
        && subscription.getStatus() != SubscriptionStatus.TRIALING) {
      throw new ConflictException("Only active or trial subscriptions can be scheduled to cancel");
    }
    if (subscription.getCurrentPeriodEnd() == null) {
      throw new BadRequestException("Subscription has no period boundary for scheduled cancellation");
    }
    if (subscription.isCancelAtPeriodEnd()) {
      return subscription;
    }

    subscription.setCancelAtPeriodEnd(true);
    subscription.setCancellationEffectiveAt(subscription.getCurrentPeriodEnd());
    CompanySubscription saved = subscriptionRepository.saveAndFlush(subscription);
    record(
        saved,
        saved.getStatus(),
        saved.getStatus(),
        saved.getPlan(),
        saved.getPlan(),
        SubscriptionTransitionType.CANCELLATION_SCHEDULED,
        actorUserId,
        "Customer requested cancellation at period end",
        saved.getPaymentPolicy(),
        saved.getExternalContractReference(),
        Instant.now());
    return saved;
  }

  @Transactional
  public CompanySubscription cancelImmediately(
      Long companyId, Long actorUserId, String reason, Instant occurredAt) {
    if (reason == null || reason.isBlank()) {
      throw new BadRequestException("Immediate cancellation requires a reason");
    }
    CompanySubscription subscription = currentForUpdate(companyId);
    SubscriptionStatus previous = subscription.getStatus();
    subscription.setStatus(SubscriptionStatus.CANCELLED);
    subscription.setCancelAtPeriodEnd(false);
    subscription.setGraceEndsAt(null);
    subscription.setCancelledAt(occurredAt);
    subscription.setCancellationEffectiveAt(occurredAt);
    subscription.setEndsAt(occurredAt);
    CompanySubscription saved = subscriptionRepository.saveAndFlush(subscription);
    record(
        saved,
        previous,
        SubscriptionStatus.CANCELLED,
        saved.getPlan(),
        saved.getPlan(),
        SubscriptionTransitionType.IMMEDIATE_CANCELLED,
        actorUserId,
        reason.trim(),
        saved.getPaymentPolicy(),
        saved.getExternalContractReference(),
        occurredAt);
    return saved;
  }

  @Transactional
  public void cancelForCompanyDeletion(Long companyId, Instant occurredAt) {
    Optional<CompanySubscription> locked = subscriptionRepository.findCurrentForUpdate(companyId);
    if (locked.isEmpty()) {
      return;
    }
    CompanySubscription subscription = locked.get();
    SubscriptionStatus previous = subscription.getStatus();
    subscription.setStatus(SubscriptionStatus.CANCELLED);
    subscription.setCancelAtPeriodEnd(false);
    subscription.setGraceEndsAt(null);
    subscription.setCancelledAt(occurredAt);
    subscription.setCancellationEffectiveAt(occurredAt);
    subscription.setEndsAt(occurredAt);
    CompanySubscription saved = subscriptionRepository.saveAndFlush(subscription);
    record(
        saved,
        previous,
        SubscriptionStatus.CANCELLED,
        saved.getPlan(),
        saved.getPlan(),
        SubscriptionTransitionType.COMPANY_DELETION_CANCELLED,
        null,
        "Company deletion requested",
        saved.getPaymentPolicy(),
        saved.getExternalContractReference(),
        occurredAt);
  }

  @Transactional
  public CompanySubscription markPastDue(Long subscriptionId, Instant occurredAt) {
    CompanySubscription subscription = requireForUpdate(subscriptionId);
    if (subscription.getStatus() == SubscriptionStatus.PAST_DUE) {
      return subscription;
    }
    if (subscription.getStatus() != SubscriptionStatus.ACTIVE) {
      throw new ConflictException("Only an active subscription can enter past due");
    }
    subscription.setStatus(SubscriptionStatus.PAST_DUE);
    subscription.setGraceEndsAt(occurredAt.plus(PAST_DUE_GRACE));
    CompanySubscription saved = subscriptionRepository.saveAndFlush(subscription);
    record(
        saved,
        SubscriptionStatus.ACTIVE,
        SubscriptionStatus.PAST_DUE,
        saved.getPlan(),
        saved.getPlan(),
        SubscriptionTransitionType.PAST_DUE,
        null,
        "Collection due-date failure",
        saved.getPaymentPolicy(),
        saved.getExternalContractReference(),
        occurredAt);
    return saved;
  }

  /**
   * B1-owned paid-renewal application boundary for B2.
   *
   * <p>B2 may call this only after verified provider payment truth has marked the exact canonical
   * renewal invoice PAID. The invoice's immutable PLATFORM BillingPeriod is the sole source of the
   * next plan-price/service-period snapshot.
   */
  @Transactional
  public CompanySubscription renewAfterConfirmedCollection(
      Long subscriptionId, Long invoiceId, Instant confirmedAt) {
    if (confirmedAt == null) {
      throw new BadRequestException("Provider-confirmed renewal time is required");
    }
    CompanySubscription subscription = requireBoundaryEligibleForUpdate(subscriptionId);
    var nextPeriod =
        invoiceService.requirePaidCanonicalPlatformInvoice(
            subscription.getCompany().getId(), invoiceId);
    return applyBoundaryTransition(
        subscription, nextPeriod, invoiceId, confirmedAt, false, "Provider-confirmed renewal");
  }

  /**
   * B1-owned zero-due commercial boundary.
   *
   * <p>This path applies a scheduled downgrade whose canonical target requires no provider
   * collection. It deliberately consumes a finalized zero-value invoice without marking it PAID
   * and without creating a Payment.
   */
  @Transactional
  public CompanySubscription applyZeroDueBoundaryTransition(
      Long subscriptionId, Long invoiceId, Instant occurredAt) {
    if (occurredAt == null) {
      throw new BadRequestException("Zero-due boundary time is required");
    }
    CompanySubscription subscription = requireBoundaryEligibleForUpdate(subscriptionId);
    var nextPeriod =
        invoiceService.requireZeroDueCanonicalPlatformInvoice(
            subscription.getCompany().getId(), invoiceId);
    return applyBoundaryTransition(
        subscription, nextPeriod, invoiceId, occurredAt, true, "Zero-due scheduled downgrade");
  }

  private CompanySubscription requireBoundaryEligibleForUpdate(Long subscriptionId) {
    CompanySubscription subscription = requireForUpdate(subscriptionId);
    if (subscription.getStatus() != SubscriptionStatus.ACTIVE
        && subscription.getStatus() != SubscriptionStatus.PAST_DUE
        && subscription.getStatus() != SubscriptionStatus.SUSPENDED) {
      throw new ConflictException("Subscription is not eligible for renewal boundary application");
    }
    return subscription;
  }

  private CompanySubscription applyBoundaryTransition(
      CompanySubscription subscription,
      com.messaging.billing.entity.BillingPeriod nextPeriod,
      Long invoiceId,
      Instant occurredAt,
      boolean zeroDue,
      String reasonPrefix) {
    if (nextPeriod.getPlan() == null
        || nextPeriod.getPlanPrice() == null
        || nextPeriod.getPlatformAmountSnapshot() == null) {
      throw new ConflictException("Boundary invoice has no canonical commercial snapshot");
    }
    if (nextPeriod.getSubscription() != null
        && !nextPeriod.getSubscription().getId().equals(subscription.getId())) {
      throw new ConflictException("Boundary period is already bound to another subscription");
    }

    if (subscription.getCurrentPeriodStart().equals(nextPeriod.getStartAt())
        && java.util.Objects.equals(subscription.getCurrentPeriodEnd(), nextPeriod.getEndAt())
        && subscription.getPlan().getId().equals(nextPeriod.getPlan().getId())
        && subscription.getPlanPrice() != null
        && subscription.getPlanPrice().getId().equals(nextPeriod.getPlanPrice().getId())) {
      return subscription;
    }
    if (subscription.getCurrentPeriodStart() == null
        || subscription.getCurrentPeriodEnd() == null
        || !subscription.getCurrentPeriodEnd().isAfter(subscription.getCurrentPeriodStart())) {
      throw new BadRequestException("Subscription has no valid platform period to renew");
    }
    if (!nextPeriod.getStartAt().equals(subscription.getCurrentPeriodEnd())) {
      throw new ConflictException(
          "Boundary invoice does not start at the subscription's canonical next boundary");
    }

    var scheduledDowngrade =
        boundaryChangeService.resolveScheduledDowngradeForBoundary(
            subscription, nextPeriod.getStartAt());
    if (zeroDue && scheduledDowngrade.isEmpty()) {
      throw new ConflictException("Zero-due boundary requires an exact scheduled downgrade");
    }

    Plan fromPlan = subscription.getPlan();
    Plan targetPlan =
        scheduledDowngrade.map(change -> change.getToPlan()).orElse(subscription.getPlan());
    PlanPrice targetPrice =
        scheduledDowngrade.map(change -> change.getToPlanPrice()).orElse(nextPeriod.getPlanPrice());

    if (!nextPeriod.getPlan().getId().equals(targetPlan.getId())
        || !nextPeriod.getPlanPrice().getId().equals(targetPrice.getId())) {
      throw new ConflictException(
          "Boundary invoice does not match the authoritative commercial state");
    }
    FinancialEffectiveTime.assertStoredActiveRange(
        targetPrice.getEffectiveFrom(), targetPrice.getEffectiveTo(), "PlanPrice");
    if (!"INR".equalsIgnoreCase(targetPrice.getCurrency())
        || targetPrice.getStatus() != com.messaging.billing.enums.RateCardStatus.ACTIVE
        || !targetPrice.getPlan().getId().equals(targetPlan.getId())
        || !FinancialEffectiveTime.includes(
            targetPrice.getEffectiveFrom(), targetPrice.getEffectiveTo(), nextPeriod.getStartAt())) {
      throw new ConflictException(
          "Boundary target PlanPrice is invalid at the canonical boundary");
    }

    boolean targetRequiresNoPayment =
        targetPrice.getBillingCycle() == com.messaging.billing.enums.BillingCycle.FREE
            || targetPrice.getAmount().compareTo(java.math.BigDecimal.ZERO) == 0;
    if (zeroDue != targetRequiresNoPayment) {
      throw new ConflictException(
          zeroDue
              ? "Zero-due boundary target requires provider collection"
              : "Paid renewal cannot activate a zero-due target through payment confirmation");
    }

    SubscriptionStatus previousStatus = subscription.getStatus();
    subscription.setPlan(targetPlan);
    subscription.setPlanPrice(targetPrice);
    subscription.setCurrentPeriodStart(nextPeriod.getStartAt());
    subscription.setCurrentPeriodEnd(nextPeriod.getEndAt());
    subscription.setBillingAnchor(nextPeriod.getStartAt());
    subscription.setStatus(SubscriptionStatus.ACTIVE);
    subscription.setGraceEndsAt(null);
    CompanySubscription saved = subscriptionRepository.saveAndFlush(subscription);

    billingPeriodService.bindPlatformPeriodToSubscription(nextPeriod.getId(), saved);
    if (scheduledDowngrade.isPresent()) {
      var change = scheduledDowngrade.get();
      boundaryChangeService.markApplied(change, invoiceId, occurredAt);
      record(
          saved,
          previousStatus,
          SubscriptionStatus.ACTIVE,
          fromPlan,
          targetPlan,
          SubscriptionTransitionType.DOWNGRADED,
          change.getRequestedByUserId(),
          reasonPrefix + " applied scheduled downgrade",
          saved.getPaymentPolicy(),
          saved.getExternalContractReference(),
          occurredAt);
    } else {
      record(
          saved,
          previousStatus,
          SubscriptionStatus.ACTIVE,
          fromPlan,
          targetPlan,
          SubscriptionTransitionType.RENEWED,
          null,
          reasonPrefix,
          saved.getPaymentPolicy(),
          saved.getExternalContractReference(),
          occurredAt);
    }
    return saved;
  }

  /**
   * Existing B1 recovery contract for B2 after verified late payment/recovery truth.
   *
   * <p>This state recovery never manufactures provider success; B2 owns provider evidence.
   */
  @Transactional
  public CompanySubscription recover(Long subscriptionId, Instant occurredAt) {
    CompanySubscription subscription = requireForUpdate(subscriptionId);
    if (subscription.getStatus() == SubscriptionStatus.ACTIVE) {
      return subscription;
    }
    if (subscription.getStatus() != SubscriptionStatus.PAST_DUE
        && subscription.getStatus() != SubscriptionStatus.SUSPENDED) {
      throw new ConflictException("Subscription is not recoverable from its current state");
    }
    SubscriptionStatus previous = subscription.getStatus();
    subscription.setStatus(SubscriptionStatus.ACTIVE);
    subscription.setGraceEndsAt(null);
    CompanySubscription saved = subscriptionRepository.saveAndFlush(subscription);
    record(
        saved,
        previous,
        SubscriptionStatus.ACTIVE,
        saved.getPlan(),
        saved.getPlan(),
        SubscriptionTransitionType.RECOVERED,
        null,
        "Provider-confirmed payment recovery",
        saved.getPaymentPolicy(),
        saved.getExternalContractReference(),
        occurredAt);
    return saved;
  }

  @Transactional
  public void recordPlanChange(
      CompanySubscription subscription,
      Plan fromPlan,
      Plan toPlan,
      SubscriptionTransitionType type,
      Long actorUserId,
      String reason,
      Instant occurredAt) {
    record(
        subscription,
        SubscriptionStatus.ACTIVE,
        SubscriptionStatus.ACTIVE,
        fromPlan,
        toPlan,
        type,
        actorUserId,
        reason,
        subscription.getPaymentPolicy(),
        subscription.getExternalContractReference(),
        occurredAt);
  }

  @Transactional
  public int applyDueLifecycle(Instant now) {
    int changed = 0;

    for (CompanySubscription subscription :
        subscriptionRepository.findPastDueForUpdate(now, CLAIM_BATCH_SIZE)) {
      if (subscription.getStatus() != SubscriptionStatus.PAST_DUE) {
        continue;
      }
      subscription.setStatus(SubscriptionStatus.SUSPENDED);
      subscriptionRepository.save(subscription);
      record(
          subscription,
          SubscriptionStatus.PAST_DUE,
          SubscriptionStatus.SUSPENDED,
          subscription.getPlan(),
          subscription.getPlan(),
          SubscriptionTransitionType.SUSPENDED,
          null,
          "Past-due grace expired",
          subscription.getPaymentPolicy(),
          subscription.getExternalContractReference(),
          now);
      changed++;
    }
    subscriptionRepository.flush();

    for (CompanySubscription subscription :
        subscriptionRepository.findExpiredTrialsForUpdate(now, CLAIM_BATCH_SIZE)) {
      if (subscription.getStatus() != SubscriptionStatus.TRIALING) {
        continue;
      }
      subscription.setStatus(SubscriptionStatus.EXPIRED);
      subscription.setEndsAt(now);
      subscriptionRepository.save(subscription);
      record(
          subscription,
          SubscriptionStatus.TRIALING,
          SubscriptionStatus.EXPIRED,
          subscription.getPlan(),
          subscription.getPlan(),
          SubscriptionTransitionType.EXPIRED,
          null,
          "Trial expired without activation",
          subscription.getPaymentPolicy(),
          subscription.getExternalContractReference(),
          now);
      changed++;
    }
    subscriptionRepository.flush();

    for (CompanySubscription subscription :
        subscriptionRepository.findDueCancellationsForUpdate(now, CLAIM_BATCH_SIZE)) {
      if (!subscription.isCancelAtPeriodEnd()
          || (subscription.getStatus() != SubscriptionStatus.ACTIVE
              && subscription.getStatus() != SubscriptionStatus.TRIALING)) {
        continue;
      }
      SubscriptionStatus previous = subscription.getStatus();
      Instant effectiveAt = subscription.getCurrentPeriodEnd();
      subscription.setStatus(SubscriptionStatus.EXPIRED);
      subscription.setCancelAtPeriodEnd(false);
      subscription.setEndsAt(effectiveAt);
      subscription.setCancellationEffectiveAt(effectiveAt);
      subscriptionRepository.save(subscription);
      record(
          subscription,
          previous,
          SubscriptionStatus.EXPIRED,
          subscription.getPlan(),
          subscription.getPlan(),
          SubscriptionTransitionType.EXPIRED,
          null,
          "Cancellation reached period boundary",
          subscription.getPaymentPolicy(),
          subscription.getExternalContractReference(),
          effectiveAt);
      changed++;
    }
    subscriptionRepository.flush();
    return changed;
  }

  private void ensureCurrentPlatformPeriodIfPresent(CompanySubscription subscription) {
    if ((subscription.getStatus() == SubscriptionStatus.ACTIVE
            || subscription.getStatus() == SubscriptionStatus.TRIALING)
        && subscription.getCurrentPeriodStart() != null
        && subscription.getCurrentPeriodEnd() != null) {
      billingPeriodService.ensurePlatformPeriod(subscription);
    }
  }

  private void record(
      CompanySubscription subscription,
      SubscriptionStatus fromStatus,
      SubscriptionStatus toStatus,
      Plan fromPlan,
      Plan toPlan,
      SubscriptionTransitionType type,
      Long actorUserId,
      String reason,
      PaymentPolicy paymentPolicy,
      String externalReference,
      Instant occurredAt) {
    SubscriptionTransition event = new SubscriptionTransition();
    event.setCompany(subscription.getCompany());
    event.setSubscription(subscription);
    event.setFromPlan(fromPlan);
    event.setToPlan(toPlan);
    event.setFromStatus(fromStatus);
    event.setToStatus(toStatus);
    event.setTransitionType(type);
    event.setActorUserId(actorUserId);
    event.setReason(reason);
    event.setEffectiveFrom(subscription.getCurrentPeriodStart());
    event.setEffectiveTo(subscription.getCurrentPeriodEnd());
    event.setPaymentPolicy(paymentPolicy);
    event.setExternalReference(externalReference);
    event.setOccurredAt(occurredAt == null ? Instant.now() : occurredAt);
    transitionRepository.save(event);
  }
}
