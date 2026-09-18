package com.messaging.inbox.entity;

import com.messaging.common.id.IdGenerator;
import com.messaging.inbox.enums.InboxParticipantType;
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
    name = "inbox_participants",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_inbox_participant_address",
        columnNames = {"conversation_id", "participant_type", "address"}),
    indexes = @Index(name = "idx_inbox_participants_conversation", columnList = "conversation_id"))
public class InboxParticipant {
  @Id private Long id;
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "conversation_id", nullable = false) private InboxConversation conversation;
  @Enumerated(EnumType.STRING)
  @Column(name = "participant_type", nullable = false, length = 32) private InboxParticipantType participantType;
  @Column(nullable = false, length = 320) private String address;
  @Column(name = "display_name", length = 255) private String displayName;
  @Column(name = "created_at", nullable = false) private Instant createdAt;
  @Column(name = "updated_at", nullable = false) private Instant updatedAt;

  @PrePersist
  private void beforeInsert() {
    if (id == null) id = IdGenerator.nextId();
    Instant now = Instant.now();
    if (createdAt == null) createdAt = now;
    updatedAt = now;
  }

  @PreUpdate
  private void beforeUpdate() { updatedAt = Instant.now(); }
}
