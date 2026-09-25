package com.messaging.broadcast.entity;

import com.messaging.broadcast.enums.*;
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
@Table(name = "broadcast_senders")
public class BroadcastSender {

  @Id private Long id;

  @Column(name = "company_id", nullable = false)
  private Long companyId;

  @Column(name = "connection_id", nullable = false)
  private Long connectionId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 40)
  private BroadcastSenderProvider provider;

  @Column(name = "from_address", nullable = false, length = 320)
  private String fromAddress;

  @Column(name = "display_name", length = 255)
  private String displayName;

  @Column(name = "reply_to_address", length = 320)
  private String replyToAddress;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private BroadcastSenderStatus status;

  @Column(name = "verified_identity", nullable = false, length = 320)
  private String verifiedIdentity;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @PrePersist
  void beforeInsert() {
    if (id == null) id = IdGenerator.nextId();
    if (status == null) status = BroadcastSenderStatus.ACTIVE;
    Instant now = Instant.now();
    if (createdAt == null) createdAt = now;
    updatedAt = now;
  }

  @PreUpdate
  void beforeUpdate() {
    updatedAt = Instant.now();
  }
}
