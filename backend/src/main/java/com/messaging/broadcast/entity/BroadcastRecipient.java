package com.messaging.broadcast.entity;

import com.messaging.broadcast.enums.BroadcastRecipientStatus;
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
@Table(name = "broadcast_recipients")
public class BroadcastRecipient {
  @Id private Long id;

  @Column(name = "run_id", nullable = false)
  private Long runId;

  @Column(name = "batch_id", nullable = false)
  private Long batchId;

  @Column(name = "company_id", nullable = false)
  private Long companyId;

  @Column(name = "connection_id", nullable = false)
  private Long connectionId;

  @Column(name = "source_member_id", nullable = false)
  private Long sourceMemberId;

  @Column(nullable = false, length = 320)
  private String destination;

  @Column(name = "normalized_destination", nullable = false, length = 320)
  private String normalizedDestination;

  @Column(name = "display_name", length = 255)
  private String displayName;

  @Column(name = "metadata_json", columnDefinition = "text")
  private String metadataJson;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private BroadcastRecipientStatus status;

  @Column(name = "attempt_count", nullable = false)
  private int attemptCount;

  @Column(name = "provider_message_id", length = 512)
  private String providerMessageId;

  @Column(name = "internet_message_id", length = 998)
  private String internetMessageId;

  @Column(name = "provider_correlation_id", nullable = false, length = 128)
  private String providerCorrelationId;

  @Column(name = "next_attempt_at")
  private Instant nextAttemptAt;

  @Column(name = "claimed_at")
  private Instant claimedAt;

  @Column(name = "accepted_at")
  private Instant acceptedAt;

  @Column(name = "bounced_at")
  private Instant bouncedAt;

  @Column(name = "failed_at")
  private Instant failedAt;

  @Column(name = "failure_code", length = 128)
  private String failureCode;

  @Column(name = "failure_reason", length = 1000)
  private String failureReason;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @PrePersist
  void beforeInsert() {
    if (id == null) id = IdGenerator.nextId();
    if (status == null) status = BroadcastRecipientStatus.PENDING;
    Instant now = Instant.now();
    if (createdAt == null) createdAt = now;
    updatedAt = now;
  }

  @PreUpdate
  void beforeUpdate() {
    updatedAt = Instant.now();
  }
}
