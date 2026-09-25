package com.messaging.channel.microsoft.entity;

import com.messaging.channel.entity.ChannelConnection;
import com.messaging.channel.microsoft.enums.MicrosoftSubscriptionStatus;
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
    name = "microsoft_subscriptions",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_microsoft_subscription_connection",
          columnNames = "connection_id"),
      @UniqueConstraint(
          name = "uk_microsoft_subscription_external",
          columnNames = "external_subscription_id")
    },
    indexes =
        @Index(
            name = "idx_microsoft_subscriptions_expiration",
            columnList = "status,expiration_at"))
public class MicrosoftSubscription {

  @Id private Long id;

  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "connection_id", nullable = false)
  private ChannelConnection connection;

  @Column(name = "external_subscription_id", nullable = false, length = 255)
  private String externalSubscriptionId;

  @Column(name = "client_state", nullable = false, length = 255)
  private String clientState;

  @Column(nullable = false, length = 512)
  private String resource;

  @Column(name = "expiration_at", nullable = false)
  private Instant expirationAt;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private MicrosoftSubscriptionStatus status;

  @Column(name = "last_error", length = 1000)
  private String lastError;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @PrePersist
  private void beforeInsert() {
    if (id == null) id = IdGenerator.nextId();
    Instant now = Instant.now();
    if (status == null) status = MicrosoftSubscriptionStatus.ACTIVE;
    if (createdAt == null) createdAt = now;
    updatedAt = now;
  }

  @PreUpdate
  private void beforeUpdate() {
    updatedAt = Instant.now();
  }
}
