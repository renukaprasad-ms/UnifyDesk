package com.messaging.billing.repository;

import com.messaging.billing.entity.SubscriptionChange;
import com.messaging.billing.enums.SubscriptionChangeStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionChangeRepository extends JpaRepository<SubscriptionChange, Long> {
  Optional<SubscriptionChange> findByIdempotencyKey(String idempotencyKey);

  List<SubscriptionChange> findByStatusAndEffectiveAtLessThanEqualOrderByEffectiveAtAsc(
      SubscriptionChangeStatus status, Instant effectiveAt);

  List<SubscriptionChange> findByCompanyIdOrderByRequestedAtDesc(Long companyId);
}
