package com.messaging.billing.service;

import com.messaging.billing.entity.CompanySubscription;
import com.messaging.billing.entity.SubscriptionChange;
import com.messaging.billing.enums.SubscriptionChangeStatus;
import com.messaging.billing.repository.SubscriptionChangeRepository;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.ConflictException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Resolves one scheduled downgrade against the exact locked subscription boundary. */
@Service
@RequiredArgsConstructor
public class SubscriptionBoundaryChangeService {

  private final SubscriptionChangeRepository changeRepository;

  @Transactional
  public Optional<SubscriptionChange> resolveScheduledDowngradeForBoundary(
      CompanySubscription subscription, Instant boundary) {
    if (subscription == null || subscription.getId() == null || boundary == null) {
      throw new BadRequestException("Subscription boundary identity is required");
    }

    List<SubscriptionChange> due =
        changeRepository.findScheduledDowngradesUpToBoundaryForUpdate(
            subscription.getId(), boundary);

    SubscriptionChange exact = null;
    for (SubscriptionChange change : due) {
      if (change.getEffectiveAt().isBefore(boundary)) {
        throw new ConflictException(
            "A stale scheduled downgrade exists before the authoritative renewal boundary");
      }
      if (exact != null) {
        throw new ConflictException(
            "Multiple scheduled downgrades exist for the same subscription boundary");
      }
      exact = change;
    }
    if (exact == null) return Optional.empty();

    assertExactBoundaryIdentity(exact, subscription, boundary);
    return Optional.of(exact);
  }

  @Transactional
  public void markApplied(SubscriptionChange change, Long invoiceId, Instant appliedAt) {
    if (change.getStatus() != SubscriptionChangeStatus.SCHEDULED) {
      throw new ConflictException("Scheduled downgrade is no longer pending at this boundary");
    }
    if (invoiceId == null || appliedAt == null) {
      throw new BadRequestException(
          "Paid downgrade application requires canonical invoice and confirmation time");
    }
    change.setInvoiceId(invoiceId);
    change.setStatus(SubscriptionChangeStatus.APPLIED);
    change.setAppliedAt(appliedAt);
    changeRepository.saveAndFlush(change);
  }

  private void assertExactBoundaryIdentity(
      SubscriptionChange change, CompanySubscription subscription, Instant boundary) {
    Long expectedPriceId =
        change.getFromPlanPrice() == null ? null : change.getFromPlanPrice().getId();
    Long actualPriceId =
        subscription.getPlanPrice() == null ? null : subscription.getPlanPrice().getId();

    if (!change.getSubscription().getId().equals(subscription.getId())
        || !change.getFromPlan().getId().equals(subscription.getPlan().getId())
        || !Objects.equals(expectedPriceId, actualPriceId)
        || !Objects.equals(change.getCurrentPeriodStart(), subscription.getCurrentPeriodStart())
        || !Objects.equals(change.getCurrentPeriodEnd(), subscription.getCurrentPeriodEnd())
        || !Objects.equals(change.getEffectiveAt(), boundary)
        || !Objects.equals(change.getCurrentPeriodEnd(), boundary)) {
      throw new ConflictException(
          "Scheduled downgrade no longer matches the exact subscription boundary");
    }
  }
}
