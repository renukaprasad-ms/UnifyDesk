package com.messaging.billing.service;

import com.messaging.billing.enums.SubscriptionStatus;
import com.messaging.billing.exception.PlanEntitlementException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Authoritative billing-status boundary for new UnifyDesk-paid Managed Service exposure.
 *
 * <p>Generic entitlements intentionally remain available for allowed PAST_DUE grace behavior. This
 * guard is narrower: the canonical current subscription may create new Managed Email/SMS provider
 * spend only while ACTIVE or TRIALING. PAST_DUE and SUSPENDED remain current subscription states
 * but are explicitly blocked from new Managed-Service financial exposure.
 */
@Service
@RequiredArgsConstructor
public class ManagedFinancialExposureGuard {

  public static final String BLOCKED = "MANAGED_FINANCIAL_EXPOSURE_BLOCKED";

  private final SubscriptionLifecycleService subscriptionLifecycleService;

  @Transactional(readOnly = true)
  public boolean isAllowed(Long companyId) {
    if (companyId == null) {
      return false;
    }
    return subscriptionLifecycleService
        .currentOptional(companyId)
        .map(subscription -> subscription.getStatus())
        .filter(
            status ->
                status == SubscriptionStatus.ACTIVE
                    || status == SubscriptionStatus.TRIALING)
        .isPresent();
  }

  @Transactional(readOnly = true)
  public void assertAllowed(Long companyId) {
    if (!isAllowed(companyId)) {
      throw new PlanEntitlementException(BLOCKED);
    }
  }
}
