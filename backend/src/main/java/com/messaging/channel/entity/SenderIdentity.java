package com.messaging.channel.entity;

import com.messaging.channel.enums.SenderIdentityStatus;
import com.messaging.channel.enums.SenderIdentityType;
import com.messaging.common.id.IdGenerator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
    name = "sender_identities",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_sender_identity_connection_type_value",
          columnNames = {"connection_id", "identity_type", "value"})
    },
    indexes = {
      @Index(name = "idx_sender_identity_connection", columnList = "connection_id"),
      @Index(name = "idx_sender_identity_connection_default", columnList = "connection_id,is_default"),
      @Index(name = "idx_sender_identity_connection_status", columnList = "connection_id,status")
    })
public class SenderIdentity {

  @Id private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "connection_id", nullable = false)
  private ChannelConnection connection;

  @Enumerated(EnumType.STRING)
  @Column(name = "identity_type", nullable = false, length = 32)
  private SenderIdentityType identityType;

  @Column(nullable = false, length = 320)
  private String value;

  @Column(name = "external_id", length = 255)
  private String externalId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private SenderIdentityStatus status;

  @Column(name = "is_default", nullable = false)
  private boolean defaultIdentity;

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
      status = SenderIdentityStatus.PENDING;
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
