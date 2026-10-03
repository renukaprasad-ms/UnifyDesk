package com.messaging.billing.repository;

import com.messaging.billing.entity.CompanySubscription;
import com.messaging.billing.enums.SubscriptionStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanySubscriptionRepository extends JpaRepository<CompanySubscription, Long> {

  Optional<CompanySubscription> findFirstByCompanyIdAndStatusOrderByStartedAtDesc(
      Long companyId, SubscriptionStatus status);

  Optional<CompanySubscription> findFirstByCompanyIdAndStatusInOrderByStartedAtDesc(
      Long companyId, Collection<SubscriptionStatus> statuses);

  List<CompanySubscription> findByCompanyIdOrderByStartedAtDesc(Long companyId);

  List<CompanySubscription> findByStatusAndGraceEndsAtLessThanEqual(
      SubscriptionStatus status, Instant graceEndsAt);

  List<CompanySubscription> findByStatusAndTrialEndsAtLessThanEqual(
      SubscriptionStatus status, Instant trialEndsAt);

  List<CompanySubscription> findByStatusAndCancelAtPeriodEndTrueAndCurrentPeriodEndLessThanEqual(
      SubscriptionStatus status, Instant currentPeriodEnd);
}
