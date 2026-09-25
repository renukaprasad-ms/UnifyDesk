package com.messaging.emailprovider.validation.entity;

import com.messaging.emailprovider.common.persistence.BaseEntity;
import com.messaging.emailprovider.validation.enums.ValidationJobStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
    name = "email_validation_jobs",
    indexes = {
      @Index(name = "idx_validation_jobs_company_created", columnList = "company_id,created_at"),
      @Index(name = "idx_validation_jobs_status", columnList = "status")
    })
public class EmailValidationJob extends BaseEntity {
  @Column(name = "company_id", nullable = false)
  private Long companyId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private ValidationJobStatus status = ValidationJobStatus.QUEUED;

  @Column(name = "total_count", nullable = false)
  private long totalCount;

  @Column(name = "duplicate_count", nullable = false)
  private long duplicateCount;

  @Column(name = "processed_count", nullable = false)
  private long processedCount;

  @Column(name = "valid_count", nullable = false)
  private long validCount;

  @Column(name = "invalid_count", nullable = false)
  private long invalidCount;

  @Column(name = "risky_count", nullable = false)
  private long riskyCount;

  @Column(name = "unknown_count", nullable = false)
  private long unknownCount;

  @Column(name = "total_batches", nullable = false)
  private int totalBatches;

  @Column(name = "completed_batches", nullable = false)
  private int completedBatches;

  @Column(name = "failed_batches", nullable = false)
  private int failedBatches;

  @Column(name = "started_at")
  private Instant startedAt;

  @Column(name = "completed_at")
  private Instant completedAt;
}
