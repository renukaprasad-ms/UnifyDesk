package com.messaging.billing.repository;

import com.messaging.billing.entity.SubscriptionTransition;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionTransitionRepository extends JpaRepository<SubscriptionTransition, Long> {
  List<SubscriptionTransition> findByCompanyIdOrderByOccurredAtDesc(Long companyId);
}
