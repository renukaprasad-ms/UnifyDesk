package com.messaging.ads.entity;

import com.messaging.ads.enums.AdCredentialType;
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
    name = "ad_provider_credentials",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_ad_provider_credential_connection_type",
          columnNames = {"connection_id", "credential_type"})
    },
    indexes = {
      @Index(name = "idx_ad_provider_credentials_connection", columnList = "connection_id"),
      @Index(name = "idx_ad_provider_credentials_expires_at", columnList = "expires_at")
    })
public class AdProviderCredential {

  @Id private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "connection_id", nullable = false)
  private AdProviderConnection connection;

  @Enumerated(EnumType.STRING)
  @Column(name = "credential_type", nullable = false, length = 40)
  private AdCredentialType credentialType;

  @Column(name = "encrypted_payload", nullable = false, columnDefinition = "text")
  private String encryptedPayload;

  @Column(name = "expires_at")
  private Instant expiresAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @PrePersist
  void beforeInsert() {
    if (id == null) id = IdGenerator.nextId();
    Instant now = Instant.now();
    if (createdAt == null) createdAt = now;
    updatedAt = now;
  }

  @PreUpdate
  void beforeUpdate() {
    updatedAt = Instant.now();
  }
}
