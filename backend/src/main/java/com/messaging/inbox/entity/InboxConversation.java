package com.messaging.inbox.entity;

import com.messaging.channel.entity.ChannelConnection;
import com.messaging.channel.enums.ProviderType;
import com.messaging.common.id.IdGenerator;
import com.messaging.inbox.enums.ConversationStatus;
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
    name = "inbox_conversations",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_inbox_conversation_external",
        columnNames = {"connection_id", "external_conversation_id"}),
    indexes = {
      @Index(name = "idx_inbox_conversations_company_last_message", columnList = "company_id,last_message_at"),
      @Index(name = "idx_inbox_conversations_connection", columnList = "connection_id")
    })
public class InboxConversation {
  @Id private Long id;
  @Column(name = "company_id", nullable = false) private Long companyId;
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "connection_id", nullable = false) private ChannelConnection connection;
  @Enumerated(EnumType.STRING)
  @Column(name = "provider_type", nullable = false, length = 40) private ProviderType providerType;
  @Column(name = "external_conversation_id", nullable = false, length = 255) private String externalConversationId;
  @Column(length = 998) private String subject;
  @Column(name = "last_message_preview", columnDefinition = "TEXT") private String lastMessagePreview;
  @Column(name = "last_message_at") private Instant lastMessageAt;
  @Column(name = "unread_count", nullable = false) private int unreadCount;
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24) private ConversationStatus status;
  @Column(name = "created_at", nullable = false) private Instant createdAt;
  @Column(name = "updated_at", nullable = false) private Instant updatedAt;

  @PrePersist
  private void beforeInsert() {
    if (id == null) id = IdGenerator.nextId();
    Instant now = Instant.now();
    if (status == null) status = ConversationStatus.OPEN;
    if (createdAt == null) createdAt = now;
    updatedAt = now;
  }

  @PreUpdate
  private void beforeUpdate() { updatedAt = Instant.now(); }
}
