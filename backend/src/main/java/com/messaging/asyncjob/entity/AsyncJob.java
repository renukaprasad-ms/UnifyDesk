package com.messaging.asyncjob.entity;

import com.messaging.asyncjob.enums.AsyncJobStatus;
import com.messaging.asyncjob.enums.AsyncJobType;
import com.messaging.common.id.IdGenerator;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
    name = "async_jobs",
    indexes = {
      @Index(name = "idx_async_jobs_user_created", columnList = "user_id,created_at"),
      @Index(name = "idx_async_jobs_company_status", columnList = "company_id,status")
    })
public class AsyncJob {
  @Id private Long id;

  @Column(name = "company_id")
  private Long companyId;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Enumerated(EnumType.STRING)
  @Column(name = "job_type", nullable = false, length = 48)
  private AsyncJobType jobType;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private AsyncJobStatus status;

  @Column(name = "total_units", nullable = false)
  private long totalUnits;

  @Column(name = "completed_units", nullable = false)
  private long completedUnits;

  @Column(name = "progress_percent", nullable = false)
  private int progressPercent;

  @Column(name = "status_message", length = 500)
  private String statusMessage;

  @Column(name = "metadata_json", columnDefinition = "TEXT")
  private String metadataJson;

  @Column(name = "error_message", length = 1000)
  private String errorMessage;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "started_at")
  private Instant startedAt;

  @Column(name = "completed_at")
  private Instant completedAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @PrePersist
  void prePersist() {
    if (id == null) id = IdGenerator.nextId();
    Instant now = Instant.now();
    if (status == null) status = AsyncJobStatus.QUEUED;
    if (createdAt == null) createdAt = now;
    updatedAt = now;
  }

  @PreUpdate
  void preUpdate() {
    updatedAt = Instant.now();
  }
}
