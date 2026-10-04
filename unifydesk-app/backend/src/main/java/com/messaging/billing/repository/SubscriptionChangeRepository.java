package com.messaging.billing.repository;

import com.messaging.billing.entity.SubscriptionChange;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface SubscriptionChangeRepository extends JpaRepository<SubscriptionChange, Long> {

  Optional<SubscriptionChange> findByCompanyIdAndIdempotencyKey(
      Long companyId, String idempotencyKey);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select c from SubscriptionChange c where c.id = :id")
  Optional<SubscriptionChange> findByIdForUpdate(@Param("id") Long id);

  @Query(
      value =
          """
          select sc.*
          from subscription_changes sc
          join company_subscriptions s on s.id = sc.subscription_id
          where sc.status = 'SCHEDULED'
            and sc.change_type = 'DOWNGRADE'
            and sc.effective_at <= :now
            and coalesce(s.payment_policy, '') <> 'CASHFREE'
          order by sc.effective_at asc, sc.id asc
          for update of sc skip locked
          limit :limit
          """,
      nativeQuery = true)
  List<SubscriptionChange> findDueDowngradesForUpdate(
      @Param("now") Instant now, @Param("limit") int limit);

  @Query(
      value =
          """
          select *
          from subscription_changes
          where subscription_id = :subscriptionId
            and change_type = 'DOWNGRADE'
            and status = 'SCHEDULED'
            and effective_at <= :boundary
          order by effective_at asc, id asc
          for update
          """,
      nativeQuery = true)
  List<SubscriptionChange> findScheduledDowngradesUpToBoundaryForUpdate(
      @Param("subscriptionId") Long subscriptionId, @Param("boundary") Instant boundary);

  List<SubscriptionChange> findByCompanyIdOrderByRequestedAtDesc(Long companyId);
}
