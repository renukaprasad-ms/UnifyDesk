package com.messaging.lifecycle.entity;

import com.messaging.common.id.IdGenerator;
import com.messaging.lifecycle.enums.DeletionPurgeStatus;
import com.messaging.lifecycle.enums.LifecycleEntityType;
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
    name = "deletion_purge_jobs",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_deletion_purge_jobs_entity",
          columnNames = {"entity_type", "entity_id"})
    },
    indexes = {
      @Index(
          name = "idx_deletion_purge_jobs_due",
          columnList = "status,purge_after,next_attempt_at")
    })
public class DeletionPurgeJob {

  @Id private Long id;

  @Enumerated(EnumType.STRING)
  @Column(name = "entity_type", nullable = false, length = 40)
  private LifecycleEntityType entityType;

  @Column(name = "entity_id", nullable = false)
  private Long entityId;

  @Column(name = "company_id")
  private Long companyId;

  @Column(name = "requested_by_user_id")
  private Long requestedByUserId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private DeletionPurgeStatus status;

  @Column(name = "purge_after", nullable = false)
  private Instant purgeAfter;

  @Column(name = "next_attempt_at")
  private Instant nextAttemptAt;

  @Column(name = "attempt_count", nullable = false)
  private int attemptCount;

  @Column(name = "last_error", length = 1000)
  private String lastError;

  @Column(name = "claimed_at")
  private Instant claimedAt;

  @Column(name = "completed_at")
  private Instant completedAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @PrePersist
  private void beforeInsert() {
    if (id == null) {
      id = IdGenerator.nextId();
    }
    Instant now = Instant.now();
    if (createdAt == null) {
      createdAt = now;
    }
    if (updatedAt == null) {
      updatedAt = now;
    }
    if (status == null) {
      status = DeletionPurgeStatus.PENDING;
    }
  }

  @PreUpdate
  private void beforeUpdate() {
    updatedAt = Instant.now();
  }
}
