package com.messaging.billing.service;

import com.messaging.billing.entity.CompanySubscription;
import com.messaging.billing.enums.BillingCycle;
import com.messaging.billing.enums.SubscriptionStatus;
import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.ConflictException;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Customer cancellation policy for paid bounded and open-ended FREE subscriptions. */
@Service
@RequiredArgsConstructor
public class CustomerSubscriptionCancellationService {

  private final SubscriptionLifecycleService lifecycleService;

  @Transactional
  public CompanySubscription cancel(Long companyId, Long actorUserId, boolean atPeriodEnd) {
    CompanySubscription current = lifecycleService.current(companyId);
    boolean openEndedFree =
        current.getCurrentPeriodEnd() == null
            && current.getPlanPrice() != null
            && current.getPlanPrice().getBillingCycle() == BillingCycle.FREE
            && current.getPlanPrice().getAmount().compareTo(BigDecimal.ZERO) == 0;

    if (openEndedFree) {
      if (current.getStatus() != SubscriptionStatus.ACTIVE
          && current.getStatus() != SubscriptionStatus.TRIALING) {
        throw new ConflictException("Only active or trial FREE subscriptions can be cancelled");
      }
      return lifecycleService.cancelImmediately(
          companyId,
          actorUserId,
          "Customer cancelled open-ended FREE subscription with no prepaid liability",
          Instant.now());
    }

    if (!atPeriodEnd) {
      throw new BadRequestException(
          "Customer immediate cancellation is not supported for paid subscriptions");
    }
    return lifecycleService.scheduleCancellation(companyId, actorUserId);
  }
}
