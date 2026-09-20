package com.messaging.inbox.entity;

import com.messaging.channel.entity.ChannelConnection;
import com.messaging.channel.enums.ProviderType;
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
    name = "channel_sync_states",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_channel_sync_state_connection_provider",
            columnNames = {"connection_id", "provider_type"}),
    indexes = @Index(name = "idx_channel_sync_states_connection", columnList = "connection_id"))
public class ChannelSyncState {
  @Id private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "connection_id", nullable = false)
  private ChannelConnection connection;

  @Enumerated(EnumType.STRING)
  @Column(name = "provider_type", nullable = false, length = 40)
  private ProviderType providerType;

  @Column(name = "cursor_value", length = 1024)
  private String cursorValue;

  @Column(name = "last_full_sync_at")
  private Instant lastFullSyncAt;

  @Column(name = "last_incremental_sync_at")
  private Instant lastIncrementalSyncAt;

  @Column(name = "watch_expiration_at")
  private Instant watchExpirationAt;

  @Column(name = "watch_renewed_at")
  private Instant watchRenewedAt;

  @Column(name = "last_notification_at")
  private Instant lastNotificationAt;

  @Column(name = "pending_history_id", length = 255)
  private String pendingHistoryId;

  @Column(name = "realtime_claimed_at")
  private Instant realtimeClaimedAt;

  @Column(name = "realtime_next_attempt_at")
  private Instant realtimeNextAttemptAt;

  @Column(name = "realtime_attempt_count", nullable = false)
  private int realtimeAttemptCount;

  @Column(name = "realtime_last_error", length = 1000)
  private String realtimeLastError;

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
