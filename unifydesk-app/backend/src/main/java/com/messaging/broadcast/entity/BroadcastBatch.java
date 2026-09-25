package com.messaging.broadcast.entity;

import com.messaging.broadcast.enums.BroadcastBatchStatus;
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
@Table(name = "broadcast_batches")
public class BroadcastBatch {
  @Id private Long id;

  @Column(name = "run_id", nullable = false)
  private Long runId;

  @Column(name = "company_id", nullable = false)
  private Long companyId;

  @Column(name = "sequence_no", nullable = false)
  private int sequenceNo;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private BroadcastBatchStatus status;

  @Column(name = "recipient_count", nullable = false)
  private int recipientCount;

  @Column(name = "processed_count", nullable = false)
  private int processedCount;

  @Column(name = "attempt_count", nullable = false)
  private int attemptCount;

  @Column(name = "next_attempt_at")
  private Instant nextAttemptAt;

  @Column(name = "claimed_at")
  private Instant claimedAt;

  @Column(name = "last_error", length = 1000)
  private String lastError;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @PrePersist
  void beforeInsert() {
    if (id == null) id = IdGenerator.nextId();
    if (status == null) status = BroadcastBatchStatus.PENDING;
    Instant now = Instant.now();
    if (createdAt == null) createdAt = now;
    updatedAt = now;
  }

  @PreUpdate
  void beforeUpdate() {
    updatedAt = Instant.now();
  }
}
