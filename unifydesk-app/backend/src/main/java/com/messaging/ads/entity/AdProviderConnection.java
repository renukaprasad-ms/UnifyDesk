package com.messaging.ads.entity;

import com.messaging.ads.enums.AdConnectionStatus;
import com.messaging.ads.enums.AdProvider;
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
    name = "ad_provider_connections",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_ad_provider_connection_business",
          columnNames = {"company_id", "provider", "external_business_id"})
    },
    indexes = {
      @Index(name = "idx_ad_provider_connections_company", columnList = "company_id"),
      @Index(
          name = "idx_ad_provider_connections_company_provider_status",
          columnList = "company_id,provider,status")
    })
public class AdProviderConnection {

  @Id private Long id;

  @Column(name = "company_id", nullable = false)
  private Long companyId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private AdProvider provider;

  @Column(name = "external_user_id", length = 255)
  private String externalUserId;

  @Column(name = "external_business_id", length = 255)
  private String externalBusinessId;

  @Column(name = "display_name", length = 160)
  private String displayName;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private AdConnectionStatus status;

  @Column(name = "created_by_user_id", nullable = false)
  private Long createdByUserId;

  @Column(name = "last_sync_at")
  private Instant lastSyncAt;

  @Column(name = "last_error", length = 1000)
  private String lastError;

  @Column(name = "provider_payload_json", columnDefinition = "text")
  private String providerPayloadJson;

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
    Instant now = Instant.now();
    if (status == null) status = AdConnectionStatus.PENDING;
    if (createdAt == null) createdAt = now;
    updatedAt = now;
  }

  @PreUpdate
  void beforeUpdate() {
    updatedAt = Instant.now();
  }
}
