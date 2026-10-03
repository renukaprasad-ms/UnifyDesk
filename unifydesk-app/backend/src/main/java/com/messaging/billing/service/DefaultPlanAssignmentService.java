package com.messaging.billing.service;

import com.messaging.billing.bootstrap.SystemBillingBootstrapService;
import com.messaging.billing.entity.CompanySubscription;
import com.messaging.billing.entity.Plan;
import com.messaging.billing.entity.PlanPrice;
import com.messaging.billing.enums.BillingCycle;
import com.messaging.billing.enums.PlanStatus;
import com.messaging.billing.enums.RateCardStatus;
import com.messaging.billing.enums.SubscriptionStatus;
import com.messaging.billing.enums.SubscriptionTransitionType;
import com.messaging.billing.repository.CompanySubscriptionRepository;
import com.messaging.billing.repository.PlanPriceRepository;
import com.messaging.billing.repository.PlanRepository;
import com.messaging.company.entity.Company;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DefaultPlanAssignmentService {

  public static final String DEFAULT_PLAN_CODE = "FREE";

  private final PlanRepository planRepository;
  private final PlanPriceRepository planPriceRepository;
  private final CompanySubscriptionRepository subscriptionRepository;
  private final SystemBillingBootstrapService billingBootstrapService;
  private final SubscriptionLifecycleService subscriptionLifecycleService;

  @Transactional
  public void assignToNewCompany(Company company) {
    if (hasEffectiveSubscription(company.getId())) {
      return;
    }

    billingBootstrapService.ensureFreePlan();

    Plan plan =
        planRepository
            .findFirstByCodeAndStatusOrderByVersionDesc(DEFAULT_PLAN_CODE, PlanStatus.PUBLISHED)
            .orElseThrow(() -> new IllegalStateException("Published FREE plan is not configured"));

    PlanPrice price =
        planPriceRepository
            .findByPlanIdAndStatusOrderByEffectiveFromDesc(plan.getId(), RateCardStatus.ACTIVE)
            .stream()
            .filter(candidate -> candidate.getBillingCycle() == BillingCycle.FREE)
            .filter(candidate -> candidate.getAmount().compareTo(BigDecimal.ZERO) == 0)
            .findFirst()
            .orElseThrow(
                () ->
                    new IllegalStateException("Published FREE plan has no active zero-cost price"));

    Instant now = Instant.now();
    CompanySubscription subscription = new CompanySubscription();
    subscription.setCompany(company);
    subscription.setPlan(plan);
    subscription.setPlanPrice(price);
    subscription.setStatus(SubscriptionStatus.ACTIVE);
    subscription.setStartedAt(now);
    subscription.setCurrentPeriodStart(now);
    subscription.setCancelAtPeriodEnd(false);
    CompanySubscription saved = subscriptionRepository.saveAndFlush(subscription);
    subscriptionLifecycleService.recordCreated(
        saved, null, "Default FREE plan assigned", SubscriptionTransitionType.CREATED);
  }

  private boolean hasEffectiveSubscription(Long companyId) {
    return subscriptionRepository
            .findFirstByCompanyIdAndStatusOrderByStartedAtDesc(companyId, SubscriptionStatus.ACTIVE)
            .isPresent()
        || subscriptionRepository
            .findFirstByCompanyIdAndStatusOrderByStartedAtDesc(
                companyId, SubscriptionStatus.TRIALING)
            .isPresent()
        || subscriptionRepository
            .findFirstByCompanyIdAndStatusOrderByStartedAtDesc(
                companyId, SubscriptionStatus.PAST_DUE)
            .isPresent()
        || subscriptionRepository
            .findFirstByCompanyIdAndStatusOrderByStartedAtDesc(
                companyId, SubscriptionStatus.SUSPENDED)
            .isPresent();
  }
}
