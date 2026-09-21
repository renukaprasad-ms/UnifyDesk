package com.messaging.broadcast.entity;

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
@Table(name = "broadcast_stats")
public class BroadcastStats {
  @Id private Long id;

  @Column(name = "run_id", nullable = false, unique = true)
  private Long runId;

  @Column(name = "total_count", nullable = false)
  private long totalCount;

  @Column(name = "pending_count", nullable = false)
  private long pendingCount;

  @Column(name = "processing_count", nullable = false)
  private long processingCount;

  @Column(name = "accepted_count", nullable = false)
  private long acceptedCount;

  @Column(name = "failed_count", nullable = false)
  private long failedCount;

  @Column(name = "bounced_count", nullable = false)
  private long bouncedCount;

  @Column(name = "unknown_count", nullable = false)
  private long unknownCount;

  @Column(name = "skipped_count", nullable = false)
  private long skippedCount;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @PrePersist
  void beforeInsert() {
    if (id == null) id = IdGenerator.nextId();
    if (updatedAt == null) updatedAt = Instant.now();
  }

  @PreUpdate
  void beforeUpdate() {
    updatedAt = Instant.now();
  }
}
