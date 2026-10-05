package com.messaging.billing.service;

import com.messaging.billing.entity.CompanySubscription;
import com.messaging.billing.enums.SubscriptionStatus;
import com.messaging.billing.exception.PlanEntitlementException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BillingExposureGuard {

  private final SubscriptionLifecycleService lifecycleService;

  @Transactional(readOnly = true)
  public void assertNewManagedFinancialExposureAllowed(Long companyId) {
    CompanySubscription subscription = lifecycleService.current(companyId);
    if (subscription.getStatus() == SubscriptionStatus.PAST_DUE
        || subscription.getStatus() == SubscriptionStatus.SUSPENDED) {
      throw new PlanEntitlementException(
          "New managed-service financial exposure is blocked while subscription is "
              + subscription.getStatus());
    }
  }
}
