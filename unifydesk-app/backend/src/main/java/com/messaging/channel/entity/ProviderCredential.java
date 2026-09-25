package com.messaging.channel.entity;

import com.messaging.channel.enums.CredentialType;
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
    name = "provider_credentials",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_provider_credential_connection_type",
          columnNames = {"connection_id", "credential_type"})
    },
    indexes = {
      @Index(name = "idx_provider_credentials_connection", columnList = "connection_id"),
      @Index(name = "idx_provider_credentials_expires_at", columnList = "expires_at")
    })
public class ProviderCredential {

  @Id private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "connection_id", nullable = false)
  private ChannelConnection connection;

  @Enumerated(EnumType.STRING)
  @Column(name = "credential_type", nullable = false, length = 40)
  private CredentialType credentialType;

  @Column(name = "encrypted_payload", nullable = false, columnDefinition = "TEXT")
  private String encryptedPayload;

  @Column(name = "expires_at")
  private Instant expiresAt;

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
