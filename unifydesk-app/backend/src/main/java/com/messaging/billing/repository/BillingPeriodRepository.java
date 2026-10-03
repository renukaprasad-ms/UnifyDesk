package com.messaging.billing.repository;

import com.messaging.billing.entity.BillingPeriod;
import com.messaging.billing.enums.BillingPeriodKind;
import com.messaging.billing.enums.BillingPeriodStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BillingPeriodRepository extends JpaRepository<BillingPeriod, Long> {
  Optional<BillingPeriod> findByCompanyIdAndKindAndStartAtAndEndAt(
      Long companyId, BillingPeriodKind kind, Instant startAt, Instant endAt);

  List<BillingPeriod> findByStatusAndEndAtLessThanEqualOrderByEndAtAsc(
      BillingPeriodStatus status, Instant endAt);

  List<BillingPeriod> findByCompanyIdOrderByStartAtDesc(Long companyId);
}
