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
    uniqueConstraints = @UniqueConstraint(
        name = "uk_channel_sync_state_connection_provider",
        columnNames = {"connection_id", "provider_type"}),
    indexes = @Index(name = "idx_channel_sync_states_connection", columnList = "connection_id"))
public class ChannelSyncState {
  @Id private Long id;
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "connection_id", nullable = false) private ChannelConnection connection;
  @Enumerated(EnumType.STRING)
  @Column(name = "provider_type", nullable = false, length = 40) private ProviderType providerType;
  @Column(name = "cursor_value", length = 1024) private String cursorValue;
  @Column(name = "last_full_sync_at") private Instant lastFullSyncAt;
  @Column(name = "last_incremental_sync_at") private Instant lastIncrementalSyncAt;
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
