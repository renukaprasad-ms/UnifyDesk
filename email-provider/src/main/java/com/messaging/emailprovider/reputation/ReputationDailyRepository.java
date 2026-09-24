package com.messaging.emailprovider.reputation;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReputationDailyRepository extends JpaRepository<ReputationDaily, Long> {

  List<ReputationDaily> findAllByCompanyIdAndMetricDateBetweenOrderByMetricDateAsc(
      Long companyId, LocalDate from, LocalDate to);

  List<ReputationDaily>
      findAllByCompanyIdAndSendingDomainIdAndMetricDateBetweenOrderByMetricDateAsc(
          Long companyId, Long sendingDomainId, LocalDate from, LocalDate to);

  @Modifying
  @Query(
      value =
          """
          INSERT INTO email_reputation_daily (
              id, company_id, sending_domain_id, metric_date,
              accepted_count, delivered_count, bounced_count, deferred_count, complaint_count,
              created_at, updated_at
          ) VALUES (
              :id, :companyId, :domainId, :metricDate,
              :accepted, :delivered, :bounced, :deferred, :complaints,
              :now, :now
          )
          ON CONFLICT (company_id, sending_domain_id, metric_date)
          DO UPDATE SET
              accepted_count = email_reputation_daily.accepted_count + EXCLUDED.accepted_count,
              delivered_count = email_reputation_daily.delivered_count + EXCLUDED.delivered_count,
              bounced_count = email_reputation_daily.bounced_count + EXCLUDED.bounced_count,
              deferred_count = email_reputation_daily.deferred_count + EXCLUDED.deferred_count,
              complaint_count = email_reputation_daily.complaint_count + EXCLUDED.complaint_count,
              updated_at = EXCLUDED.updated_at
          """,
      nativeQuery = true)
  void increment(
      @Param("id") Long id,
      @Param("companyId") Long companyId,
      @Param("domainId") Long domainId,
      @Param("metricDate") LocalDate metricDate,
      @Param("accepted") long accepted,
      @Param("delivered") long delivered,
      @Param("bounced") long bounced,
      @Param("deferred") long deferred,
      @Param("complaints") long complaints,
      @Param("now") Instant now);
}
