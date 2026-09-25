package com.messaging.broadcast.entity;

import com.messaging.broadcast.enums.BroadcastOutboxStatus;
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
@Table(name = "broadcast_outbox")
public class BroadcastOutbox {
  @Id private Long id;

  @Column(name = "company_id", nullable = false)
  private Long companyId;

  @Column(name = "aggregate_type", nullable = false, length = 48)
  private String aggregateType;

  @Column(name = "aggregate_id", nullable = false)
  private Long aggregateId;

  @Column(name = "event_type", nullable = false, length = 96)
  private String eventType;

  @Column(name = "payload_json", nullable = false, columnDefinition = "text")
  private String payloadJson;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private BroadcastOutboxStatus status;

  @Column(name = "attempt_count", nullable = false)
  private int attemptCount;

  @Column(name = "available_at", nullable = false)
  private Instant availableAt;

  @Column(name = "claimed_at")
  private Instant claimedAt;

  @Column(name = "published_at")
  private Instant publishedAt;

  @Column(name = "last_error", length = 1000)
  private String lastError;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @PrePersist
  void beforeInsert() {
    if (id == null) id = IdGenerator.nextId();
    if (status == null) status = BroadcastOutboxStatus.PENDING;
    Instant now = Instant.now();
    if (availableAt == null) availableAt = now;
    if (createdAt == null) createdAt = now;
    updatedAt = now;
  }

  @PreUpdate
  void beforeUpdate() {
    updatedAt = Instant.now();
  }
}
