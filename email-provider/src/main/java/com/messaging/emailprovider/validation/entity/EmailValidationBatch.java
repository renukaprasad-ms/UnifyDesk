package com.messaging.emailprovider.validation.entity;

import com.messaging.emailprovider.common.persistence.BaseEntity;
import com.messaging.emailprovider.validation.enums.ValidationBatchStatus;
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
    name = "email_validation_batches",
    indexes = {
      @Index(name = "idx_validation_batches_job", columnList = "job_id,batch_sequence"),
      @Index(name = "idx_validation_batches_retry", columnList = "status,next_attempt_at")
    })
public class EmailValidationBatch extends BaseEntity {
  @Column(name = "job_id", nullable = false)
  private Long jobId;

  @Column(name = "batch_sequence", nullable = false)
  private int batchSequence;

  @Column(name = "item_count", nullable = false)
  private int itemCount;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private ValidationBatchStatus status = ValidationBatchStatus.PENDING;

  @Column(nullable = false)
  private int attempts;

  @Column(name = "next_attempt_at")
  private Instant nextAttemptAt;

  @Column(name = "started_at")
  private Instant startedAt;

  @Column(name = "completed_at")
  private Instant completedAt;

  @Column(name = "last_error", length = 2048)
  private String lastError;
}
