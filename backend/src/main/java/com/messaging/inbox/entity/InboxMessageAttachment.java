package com.messaging.inbox.entity;

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
@Table(
    name = "inbox_message_attachments",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_inbox_message_attachment_external",
            columnNames = {"message_id", "external_attachment_id"}),
    indexes = @Index(name = "idx_inbox_attachments_message", columnList = "message_id"))
public class InboxMessageAttachment {
  @Id private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "message_id", nullable = false)
  private InboxMessage message;

  @Column(name = "external_attachment_id", nullable = false, columnDefinition = "text")
  private String externalAttachmentId;

  @Column(columnDefinition = "text")
  private String filename;

  @Column(name = "mime_type", columnDefinition = "text")
  private String mimeType;

  @Column(name = "size_bytes")
  private Long sizeBytes;

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
