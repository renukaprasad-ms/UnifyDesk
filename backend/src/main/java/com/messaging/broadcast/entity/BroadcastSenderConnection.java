package com.messaging.broadcast.entity;

import com.messaging.broadcast.enums.*;
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
@Table(name = "broadcast_sender_connections")
public class BroadcastSenderConnection {

  @Id private Long id;

  @Column(name = "company_id", nullable = false)
  private Long companyId;

  @Column(name = "channel_connection_id", unique = true)
  private Long channelConnectionId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 40)
  private BroadcastSenderProvider provider;

  @Enumerated(EnumType.STRING)
  @Column(name = "management_mode", nullable = false, length = 32)
  private BroadcastSenderManagementMode managementMode;

  @Enumerated(EnumType.STRING)
  @Column(name = "auth_mode", nullable = false, length = 32)
  private BroadcastSenderAuthMode authMode;

  @Column(nullable = false, length = 160)
  private String name;

  @Column(nullable = false, length = 64)
  private String region;

  @Column(name = "role_arn", length = 512)
  private String roleArn;

  @Column(name = "external_id", length = 255)
  private String externalId;

  @Column(name = "encrypted_credentials", columnDefinition = "text")
  private String encryptedCredentials;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private BroadcastSenderConnectionStatus status;

  @Column(name = "production_access_enabled", nullable = false)
  private boolean productionAccessEnabled;

  @Column(name = "sending_enabled", nullable = false)
  private boolean sendingEnabled;

  @Column(name = "max_24h_send")
  private Double max24HourSend;

  @Column(name = "max_send_rate")
  private Double maxSendRate;

  @Column(name = "sent_last_24h")
  private Double sentLast24Hours;

  @Column(name = "last_validated_at")
  private Instant lastValidatedAt;

  @Column(name = "created_by_user_id", nullable = false)
  private Long createdByUserId;

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
    if (channelConnectionId == null) channelConnectionId = id;
    if (managementMode == null) managementMode = BroadcastSenderManagementMode.CUSTOMER_MANAGED;
    if (status == null) status = BroadcastSenderConnectionStatus.ACTIVE;
    Instant now = Instant.now();
    if (createdAt == null) createdAt = now;
    updatedAt = now;
  }

  @PreUpdate
  void beforeUpdate() {
    updatedAt = Instant.now();
  }
}
