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
          select *
          from subscription_changes
          where status = 'SCHEDULED'
            and change_type = 'DOWNGRADE'
            and effective_at <= :now
          order by effective_at asc, id asc
          for update skip locked
          limit :limit
          """,
      nativeQuery = true)
  List<SubscriptionChange> findDueDowngradesForUpdate(
      @Param("now") Instant now, @Param("limit") int limit);

  List<SubscriptionChange> findByCompanyIdOrderByRequestedAtDesc(Long companyId);
}
