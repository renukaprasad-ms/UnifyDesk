package com.messaging.channel.entity;

import com.messaging.channel.enums.ChannelConnectionStatus;
import com.messaging.channel.enums.ChannelType;
import com.messaging.channel.enums.ProviderType;
import com.messaging.common.id.IdGenerator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
    name = "channel_connections",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_channel_connection_provider_account",
          columnNames = {"company_id", "provider_type", "external_account_id"})
    },
    indexes = {
      @Index(name = "idx_channel_connections_company", columnList = "company_id"),
      @Index(
          name = "idx_channel_connections_company_channel_status",
          columnList = "company_id,channel_type,status"),
      @Index(
          name = "idx_channel_connections_provider_external",
          columnList = "provider_type,external_account_id")
    })
public class ChannelConnection {

  @Id private Long id;

  @Column(name = "company_id", nullable = false)
  private Long companyId;

  @Enumerated(EnumType.STRING)
  @Column(name = "channel_type", nullable = false, length = 24)
  private ChannelType channelType;

  @Enumerated(EnumType.STRING)
  @Column(name = "provider_type", nullable = false, length = 40)
  private ProviderType providerType;

  @Column(nullable = false, length = 120)
  private String name;

  @Column(name = "external_account_id", length = 255)
  private String externalAccountId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private ChannelConnectionStatus status;

  @Column(name = "created_by_user_id", nullable = false)
  private Long createdByUserId;

  @Column(name = "last_sync_at")
  private Instant lastSyncAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @PrePersist
  private void beforeInsert() {
    if (id == null) {
      id = IdGenerator.nextId();
    }
    Instant now = Instant.now();
    if (status == null) {
      status = ChannelConnectionStatus.PENDING;
    }
    if (createdAt == null) {
      createdAt = now;
    }
    if (updatedAt == null) {
      updatedAt = now;
    }
  }

  @PreUpdate
  private void beforeUpdate() {
    updatedAt = Instant.now();
  }
}
