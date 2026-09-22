package com.messaging.broadcast.entity;

import com.messaging.broadcast.enums.BroadcastMessageStatus;
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
@Table(name = "broadcast_messages")
public class BroadcastMessage {

  @Id private Long id;

  @Column(name = "company_id", nullable = false)
  private Long companyId;

  @Column(name = "group_id", nullable = false)
  private Long groupId;

  /** Legacy mailbox sender reference retained only for historical broadcasts. */
  @Column(name = "connection_id")
  private Long connectionId;

  @Column(name = "sender_id")
  private Long senderId;

  @Enumerated(EnumType.STRING)
  @Column(name = "channel_type", nullable = false, length = 24)
  private ChannelType channelType;

  @Column(length = 998)
  private String subject;

  @Column(name = "body_text", nullable = false, columnDefinition = "text")
  private String bodyText;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private BroadcastMessageStatus status;

  @Column(name = "scheduled_at")
  private Instant scheduledAt;

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
    if (status == null) status = BroadcastMessageStatus.DRAFT;
    Instant now = Instant.now();
    if (createdAt == null) createdAt = now;
    updatedAt = now;
  }

  @PreUpdate
  void beforeUpdate() {
    updatedAt = Instant.now();
  }
}
