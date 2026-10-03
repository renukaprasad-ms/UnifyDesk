package com.messaging.billing.repository;

import com.messaging.billing.entity.SubscriptionTransition;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionTransitionRepository extends JpaRepository<SubscriptionTransition, Long> {
  List<SubscriptionTransition> findByCompanyIdOrderByOccurredAtDesc(Long companyId);

  Optional<SubscriptionTransition>
      findFirstByCompanyIdAndOccurredAtLessThanEqualAndToPlanIsNotNullOrderByOccurredAtDescIdDesc(
          Long companyId, Instant occurredAt);
}
