package com.messaging.channel.gmail.entity;

import com.messaging.channel.gmail.enums.GmailSyncBatchStatus;
import com.messaging.common.id.IdGenerator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
    name = "gmail_sync_batches",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_gmail_sync_batches_job_index",
          columnNames = {"job_id", "batch_index"})
    })
public class GmailSyncBatch {

  @Id private Long id;

  @Column(name = "job_id", nullable = false)
  private Long jobId;

  @Column(name = "company_id", nullable = false)
  private Long companyId;

  @Column(name = "connection_id", nullable = false)
  private Long connectionId;

  @Column(name = "batch_index", nullable = false)
  private int batchIndex;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private GmailSyncBatchStatus status;

  @Column(name = "message_ids_json", nullable = false, columnDefinition = "TEXT")
  private String messageIdsJson;

  @Column(name = "message_count", nullable = false)
  private int messageCount;

  @Column(name = "processed_count", nullable = false)
  private int processedCount;

  @Column(name = "failed_count", nullable = false)
  private int failedCount;

  @Column(name = "attempt_count", nullable = false)
  private int attemptCount;

  @Column(name = "last_error", length = 1000)
  private String lastError;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "completed_at")
  private Instant completedAt;

  @PrePersist
  void prePersist() {
    if (id == null) id = IdGenerator.nextId();
    if (status == null) status = GmailSyncBatchStatus.PENDING;
    Instant now = Instant.now();
    if (createdAt == null) createdAt = now;
    updatedAt = now;
  }

  @PreUpdate
  void preUpdate() {
    updatedAt = Instant.now();
  }
}
