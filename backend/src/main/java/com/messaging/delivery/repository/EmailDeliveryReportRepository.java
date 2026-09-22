package com.messaging.delivery.repository;

import com.messaging.delivery.entity.EmailDeliveryReport;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface EmailDeliveryReportRepository extends JpaRepository<EmailDeliveryReport, Long> {

  Optional<EmailDeliveryReport> findBySourceInboxMessageId(Long sourceInboxMessageId);

  @Query(
      value =
          """
          select *
          from email_delivery_reports
          where processed_at is null
            and (next_attempt_at is null or next_attempt_at <= :now)
            and (claimed_at is null or claimed_at < :staleBefore)
          order by id asc
          for update skip locked
          limit :limit
          """,
      nativeQuery = true)
  List<EmailDeliveryReport> findClaimableForUpdate(
      @Param("now") Instant now,
      @Param("staleBefore") Instant staleBefore,
      @Param("limit") int limit);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select report from EmailDeliveryReport report where report.id = :id")
  Optional<EmailDeliveryReport> findByIdForUpdate(@Param("id") Long id);
}
