package com.messaging.billing.service;

import com.messaging.billing.entity.CompanySubscription;
import com.messaging.billing.entity.Plan;
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
