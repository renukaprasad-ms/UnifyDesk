package com.messaging.billing.service;

import com.messaging.billing.entity.CompanySubscription;
import com.messaging.billing.enums.SubscriptionStatus;
import com.messaging.billing.exception.PlanEntitlementException;
import com.messaging.billing.repository.CompanySubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Authoritative billing-status boundary for new UnifyDesk-paid Managed Service exposure.
 *
 * <p>Generic entitlements intentionally remain available for allowed PAST_DUE grace behavior. This
 * guard is narrower: only an ACTIVE platform subscription may create new Managed Email/SMS provider
 * spend in Billing R1.
 */
@Service
@RequiredArgsConstructor
public class ManagedFinancialExposureGuard {

  public static final String BLOCKED = "MANAGED_FINANCIAL_EXPOSURE_BLOCKED";

  private final CompanySubscriptionRepository subscriptionRepository;

  @Transactional(readOnly = true)
  public boolean isAllowed(Long companyId) {
    if (companyId == null) {
      return false;
    }
    return subscriptionRepository
        .findFirstByCompanyIdOrderByStartedAtDescIdDesc(companyId)
        .map(CompanySubscription::getStatus)
        .filter(SubscriptionStatus.ACTIVE::equals)
        .isPresent();
  }

  @Transactional(readOnly = true)
  public void assertAllowed(Long companyId) {
    if (!isAllowed(companyId)) {
      throw new PlanEntitlementException(BLOCKED);
    }
  }
}
