package com.messaging.broadcast.entity;

import com.messaging.broadcast.enums.BroadcastAttemptStatus;
import com.messaging.broadcast.enums.BroadcastSenderProvider;
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
@Table(name = "broadcast_delivery_attempts")
public class BroadcastDeliveryAttempt {

  @Id private Long id;

  @Column(name = "recipient_id", nullable = false)
  private Long recipientId;

  @Column(name = "run_id", nullable = false)
  private Long runId;

  @Column(name = "attempt_no", nullable = false)
  private int attemptNo;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private BroadcastAttemptStatus status;

  @Enumerated(EnumType.STRING)
  @Column(name = "provider_type", nullable = false, length = 40)
  private BroadcastSenderProvider providerType;

  @Column(name = "provider_message_id", length = 512)
  private String providerMessageId;

  @Column(name = "started_at", nullable = false)
  private Instant startedAt;

  @Column(name = "completed_at")
  private Instant completedAt;

  @Column(name = "error_code", length = 128)
  private String errorCode;

  @Column(name = "error_message", length = 1000)
  private String errorMessage;

  @PrePersist
  void beforeInsert() {
    if (id == null) id = IdGenerator.nextId();
    if (startedAt == null) startedAt = Instant.now();
  }
}
