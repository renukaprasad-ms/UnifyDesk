package com.messaging.broadcast.entity;

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
@Table(name = "broadcast_groups")
public class BroadcastGroup {
  @Id private Long id;

  @Column(name = "company_id", nullable = false)
  private Long companyId;

  @Enumerated(EnumType.STRING)
  @Column(name = "channel_type", nullable = false, length = 24)
  private ChannelType channelType;

  @Column(nullable = false, length = 160)
  private String name;

  @Column(length = 500)
  private String description;

  @Column(name = "member_count", nullable = false)
  private long memberCount;

  @Column(name = "created_by_user_id", nullable = false)
  private Long createdByUserId;

  @Version
  @Column(nullable = false)
  private long version;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @PrePersist
  void beforeInsert() {
    if (id == null) id = IdGenerator.nextId();
    Instant now = Instant.now();
    if (createdAt == null) createdAt = now;
    updatedAt = now;
  }

  @PreUpdate
  void beforeUpdate() {
    updatedAt = Instant.now();
  }
}
