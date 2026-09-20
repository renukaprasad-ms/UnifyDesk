package com.messaging.channel.microsoft.entity;

import com.messaging.channel.entity.ChannelConnection;
import com.messaging.channel.microsoft.enums.MicrosoftSyncStatus;
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
    name = "microsoft_sync_states",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_microsoft_sync_state_connection_folder",
            columnNames = {"connection_id", "folder_key"}),
    indexes = {
      @Index(
          name = "idx_microsoft_sync_states_claim",
          columnList = "status,next_attempt_at,connection_id"),
      @Index(name = "idx_microsoft_sync_states_connection", columnList = "connection_id")
    })
public class MicrosoftSyncState {

  @Id private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "connection_id", nullable = false)
  private ChannelConnection connection;

  @Column(name = "folder_key", nullable = false, length = 32)
  private String folderKey;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private MicrosoftSyncStatus status;

  @Column(name = "next_url", columnDefinition = "TEXT")
  private String nextUrl;

  @Column(name = "delta_url", columnDefinition = "TEXT")
  private String deltaUrl;

  @Column(name = "resync_requested", nullable = false)
  private boolean resyncRequested;

  @Column(name = "next_attempt_at")
  private Instant nextAttemptAt;

  @Column(name = "attempt_count", nullable = false)
  private int attemptCount;

  @Column(name = "last_error", length = 1000)
  private String lastError;

  @Column(name = "claimed_at")
  private Instant claimedAt;

  @Column(name = "last_synced_at")
  private Instant lastSyncedAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @PrePersist
  private void beforeInsert() {
    if (id == null) id = IdGenerator.nextId();
    Instant now = Instant.now();
    if (status == null) status = MicrosoftSyncStatus.PENDING;
    if (createdAt == null) createdAt = now;
    updatedAt = now;
  }

  @PreUpdate
  private void beforeUpdate() {
    updatedAt = Instant.now();
  }
}
