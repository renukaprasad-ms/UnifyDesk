package com.messaging.broadcast.entity;

import com.messaging.broadcast.enums.BroadcastSuppressionReason;
import com.messaging.channel.enums.ChannelType;
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
@Table(name = "broadcast_suppressions")
public class BroadcastSuppression {
  @Id private Long id;

  @Column(name = "company_id", nullable = false)
  private Long companyId;

  @Enumerated(EnumType.STRING)
  @Column(name = "channel_type", nullable = false, length = 24)
  private ChannelType channelType;

  @Column(name = "normalized_destination", nullable = false, length = 320)
  private String normalizedDestination;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private BroadcastSuppressionReason reason;

  @Column(name = "source_recipient_id")
  private Long sourceRecipientId;

  @Column(nullable = false)
  private boolean active;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @PrePersist
  void beforeInsert() {
    if (id == null) id = IdGenerator.nextId();
    active = true;
    Instant now = Instant.now();
    if (createdAt == null) createdAt = now;
    updatedAt = now;
  }

  @PreUpdate
  void beforeUpdate() {
    updatedAt = Instant.now();
  }
}
