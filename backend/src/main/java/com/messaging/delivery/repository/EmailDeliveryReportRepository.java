package com.messaging.delivery.repository;

import com.messaging.delivery.entity.EmailDeliveryReport;
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
          order by id asc
          for update skip locked
          limit :limit
          """,
      nativeQuery = true)
  List<EmailDeliveryReport> findUnprocessedForUpdate(@Param("limit") int limit);
}
