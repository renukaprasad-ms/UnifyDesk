package com.messaging.broadcast.entity;

import com.messaging.broadcast.enums.BroadcastSenderProvider;
import com.messaging.broadcast.enums.BroadcastSetupStatus;
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
@Table(name = "broadcast_setup_sessions")
public class BroadcastSetupSession {

  @Id private Long id;

  @Column(name = "company_id", nullable = false)
  private Long companyId;

  @Enumerated(EnumType.STRING)
  @Column(name = "channel_type", nullable = false, length = 32)
  private ChannelType channelType;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 40)
  private BroadcastSenderProvider provider;

  @Column(name = "current_step", nullable = false)
  private int currentStep;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private BroadcastSetupStatus status;

  @Column(name = "setup_data_json", columnDefinition = "text")
  private String setupDataJson;

  @Column(name = "created_by_user_id", nullable = false)
  private Long createdByUserId;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "completed_at")
  private Instant completedAt;

  @PrePersist
  void beforeInsert() {
    if (id == null) id = IdGenerator.nextId();
    if (status == null) status = BroadcastSetupStatus.IN_PROGRESS;
    Instant now = Instant.now();
    if (createdAt == null) createdAt = now;
    updatedAt = now;
  }

  @PreUpdate
  void beforeUpdate() {
    updatedAt = Instant.now();
  }
}
