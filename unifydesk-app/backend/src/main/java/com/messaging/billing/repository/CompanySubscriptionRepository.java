package com.messaging.billing.repository;

import com.messaging.billing.entity.CompanySubscription;
import com.messaging.billing.enums.SubscriptionStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CompanySubscriptionRepository extends JpaRepository<CompanySubscription, Long> {

  Optional<CompanySubscription> findFirstByCompanyIdAndStatusOrderByStartedAtDesc(
      Long companyId, SubscriptionStatus status);

  Optional<CompanySubscription> findFirstByCompanyIdAndStatusInOrderByStartedAtDesc(
      Long companyId, Collection<SubscriptionStatus> statuses);

  List<CompanySubscription> findByCompanyIdOrderByStartedAtDesc(Long companyId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select s from CompanySubscription s where s.id = :id")
  Optional<CompanySubscription> findByIdForUpdate(@Param("id") Long id);

  @Query(
      value =
          """
          select *
          from company_subscriptions
          where company_id = :companyId
            and status in ('ACTIVE', 'TRIALING', 'PAST_DUE', 'SUSPENDED')
          order by started_at desc, id desc
          limit 1
          for update
          """,
      nativeQuery = true)
  Optional<CompanySubscription> findCurrentForUpdate(@Param("companyId") Long companyId);

  @Query(
      value =
          """
          select *
          from company_subscriptions
          where status = 'PAST_DUE'
            and grace_ends_at is not null
            and grace_ends_at <= :now
          order by grace_ends_at asc, id asc
          for update skip locked
          limit :limit
          """,
      nativeQuery = true)
  List<CompanySubscription> findPastDueForUpdate(
      @Param("now") Instant now, @Param("limit") int limit);

  @Query(
      value =
          """
          select *
          from company_subscriptions
          where status = 'TRIALING'
            and trial_ends_at is not null
            and trial_ends_at <= :now
          order by trial_ends_at asc, id asc
          for update skip locked
          limit :limit
          """,
      nativeQuery = true)
  List<CompanySubscription> findExpiredTrialsForUpdate(
      @Param("now") Instant now, @Param("limit") int limit);

  @Query(
      value =
          """
          select *
          from company_subscriptions
          where status in ('ACTIVE', 'TRIALING')
            and cancel_at_period_end = true
            and current_period_end is not null
            and current_period_end <= :now
          order by current_period_end asc, id asc
          for update skip locked
          limit :limit
          """,
      nativeQuery = true)
  List<CompanySubscription> findDueCancellationsForUpdate(
      @Param("now") Instant now, @Param("limit") int limit);
}
