package com.messaging.billing.repository;

import com.messaging.billing.entity.SubscriptionCheckoutIntent;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SubscriptionCheckoutIntentRepository
    extends JpaRepository<SubscriptionCheckoutIntent, Long> {

  Optional<SubscriptionCheckoutIntent> findByCompanyIdAndIdempotencyKey(
      Long companyId, String idempotencyKey);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select i from SubscriptionCheckoutIntent i where i.id = :id")
  Optional<SubscriptionCheckoutIntent> findByIdForUpdate(@Param("id") Long id);
}
