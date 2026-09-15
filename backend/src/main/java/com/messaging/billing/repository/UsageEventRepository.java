package com.messaging.billing.repository;

import com.messaging.billing.entity.UsageEvent;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UsageEventRepository extends JpaRepository<UsageEvent, Long> {

  List<UsageEvent> findByCompanyIdOrderByOccurredAtDesc(Long companyId);
}
