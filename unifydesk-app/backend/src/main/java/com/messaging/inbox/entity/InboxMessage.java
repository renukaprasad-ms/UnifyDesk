package com.messaging.inbox.entity;

import com.messaging.channel.entity.ChannelConnection;
import com.messaging.common.id.IdGenerator;
import com.messaging.inbox.enums.InboxMessageDirection;
import com.messaging.inbox.enums.InboxMessageStatus;
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
    name = "inbox_messages",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_inbox_message_external",
            columnNames = {"connection_id", "external_message_id"}),
    indexes = {
      @Index(name = "idx_inbox_messages_conversation_sent", columnList = "conversation_id,sent_at"),
      @Index(name = "idx_inbox_messages_connection", columnList = "connection_id"),
      @Index(name = "idx_inbox_messages_unread", columnList = "conversation_id,is_read")
    })
public class InboxMessage {
  @Id private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "conversation_id", nullable = false)
  private InboxConversation conversation;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "connection_id", nullable = false)
  private ChannelConnection connection;

  @Column(name = "external_message_id", nullable = false, length = 255)
  private String externalMessageId;

  @Column(name = "internet_message_id", length = 998)
  private String internetMessageId;

  @Column(name = "provider_history_id", length = 255)
  private String providerHistoryId;

  @Column(name = "provider_folder_key", length = 64)
  private String providerFolderKey;

  @Column(name = "provider_sync_marker")
  private Long providerSyncMarker;

  @Column(name = "from_address", length = 320)
  private String fromAddress;

  @Column(name = "reply_to_address", length = 320)
  private String replyToAddress;

  @Column(name = "to_addresses", columnDefinition = "TEXT")
  private String toAddresses;

  @Column(name = "cc_addresses", columnDefinition = "TEXT")
  private String ccAddresses;

  @Column(name = "bcc_addresses", columnDefinition = "TEXT")
  private String bccAddresses;

  @Column(name = "in_reply_to", length = 998)
  private String inReplyTo;

  @Column(name = "references_header", columnDefinition = "TEXT")
  private String referencesHeader;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private InboxMessageDirection direction;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private InboxMessageStatus status;

  @Column(length = 998)
  private String subject;

  @Column(columnDefinition = "TEXT")
  private String snippet;

  @Column(name = "body_text", columnDefinition = "TEXT")
  private String bodyText;

  @Column(name = "body_html", columnDefinition = "TEXT")
  private String bodyHtml;

  @Column(name = "sent_at")
  private Instant sentAt;

  @Column(name = "is_read", nullable = false)
  private boolean read;

  @Column(name = "storage_bytes", nullable = false)
  private long storageBytes;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @PrePersist
  private void beforeInsert() {
    if (id == null) id = IdGenerator.nextId();
    Instant now = Instant.now();
    if (createdAt == null) createdAt = now;
    updatedAt = now;
  }

  @PreUpdate
  private void beforeUpdate() {
    updatedAt = Instant.now();
  }
}
