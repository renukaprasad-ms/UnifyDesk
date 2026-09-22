package com.messaging.broadcast.entity;

import com.messaging.broadcast.enums.BroadcastRunStatus;
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
@Table(name = "broadcast_runs")
public class BroadcastRun {

  @Id private Long id;

  @Column(name = "broadcast_message_id", nullable = false)
  private Long broadcastMessageId;

  @Column(name = "company_id", nullable = false)
  private Long companyId;

  @Column(name = "group_id", nullable = false)
  private Long groupId;

  /** Legacy mailbox sender reference retained only for historical runs. */
  @Column(name = "connection_id")
  private Long connectionId;

  @Column(name = "sender_id")
  private Long senderId;

  @Enumerated(EnumType.STRING)
  @Column(name = "provider_type", nullable = false, length = 40)
  private BroadcastSenderProvider providerType;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private BroadcastRunStatus status;

  @Column(name = "subject_snapshot", length = 998)
  private String subjectSnapshot;

  @Column(name = "body_text_snapshot", nullable = false, columnDefinition = "text")
  private String bodyTextSnapshot;

  @Column(name = "body_html_snapshot", columnDefinition = "text")
  private String bodyHtmlSnapshot;

  @Column(name = "snapshot_cursor_member_id")
  private Long snapshotCursorMemberId;

  @Column(name = "snapshot_max_member_id")
  private Long snapshotMaxMemberId;

  @Column(name = "snapshot_complete", nullable = false)
  private boolean snapshotComplete;

  @Column(name = "total_recipients", nullable = false)
  private long totalRecipients;

  @Column(name = "batch_count", nullable = false)
  private int batchCount;

  @Column(name = "started_at")
  private Instant startedAt;

  @Column(name = "completed_at")
  private Instant completedAt;

  @Column(name = "claimed_at")
  private Instant claimedAt;

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
    if (status == null) status = BroadcastRunStatus.PREPARING;
    Instant now = Instant.now();
    if (createdAt == null) createdAt = now;
    updatedAt = now;
  }

  @PreUpdate
  void beforeUpdate() {
    updatedAt = Instant.now();
  }
}
