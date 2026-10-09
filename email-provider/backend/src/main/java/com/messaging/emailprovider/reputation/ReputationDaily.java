package com.messaging.emailprovider.reputation;

import com.messaging.emailprovider.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
    name = "email_reputation_daily",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_email_reputation_daily",
          columnNames = {"company_id", "sending_domain_id", "metric_date"})
    },
    indexes = {
      @Index(name = "idx_email_reputation_company_date", columnList = "company_id,metric_date")
    })
public class ReputationDaily extends BaseEntity {

  @Column(name = "company_id", nullable = false)
  private Long companyId;

  @Column(name = "sending_domain_id", nullable = false)
  private Long sendingDomainId;

  @Column(name = "metric_date", nullable = false)
  private LocalDate metricDate;

  @Column(name = "accepted_count", nullable = false)
  private long acceptedCount;

  @Column(name = "delivered_count", nullable = false)
  private long deliveredCount;

  @Column(name = "bounced_count", nullable = false)
  private long bouncedCount;

  @Column(name = "deferred_count", nullable = false)
  private long deferredCount;

  @Column(name = "complaint_count", nullable = false)
  private long complaintCount;
}
