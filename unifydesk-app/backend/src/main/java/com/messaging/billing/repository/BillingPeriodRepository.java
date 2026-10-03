package com.messaging.billing.repository;

import com.messaging.billing.entity.BillingPeriod;
import com.messaging.billing.enums.BillingPeriodKind;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BillingPeriodRepository extends JpaRepository<BillingPeriod, Long> {

  Optional<BillingPeriod> findByCompanyIdAndKindAndStartAtAndEndAt(
      Long companyId, BillingPeriodKind kind, Instant startAt, Instant endAt);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select p from BillingPeriod p where p.id = :id")
  Optional<BillingPeriod> findByIdForUpdate(@Param("id") Long id);

  @Query(
      value =
          """
          select *
          from billing_periods
          where status = 'OPEN'
            and end_at <= :now
          order by end_at asc, id asc
          for update skip locked
          limit :limit
          """,
      nativeQuery = true)
  List<BillingPeriod> findDueOpenForUpdate(
      @Param("now") Instant now, @Param("limit") int limit);

  @Query(
      value =
          """
          select *
          from billing_periods
          where status = 'CLOSING'
          order by end_at asc, id asc
          for update skip locked
          limit :limit
          """,
      nativeQuery = true)
  List<BillingPeriod> findClosingForUpdate(@Param("limit") int limit);

  List<BillingPeriod> findByCompanyIdOrderByStartAtDesc(Long companyId);
}
