package com.messaging.ads.entity;

import com.messaging.ads.enums.AdAccountStatus;
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
    name = "ad_accounts",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_ad_account_provider_external",
          columnNames = {"company_id", "provider", "external_account_id"})
    },
    indexes = {
      @Index(name = "idx_ad_accounts_company", columnList = "company_id"),
      @Index(
          name = "idx_ad_accounts_company_provider_status",
          columnList = "company_id,provider,status"),
      @Index(name = "idx_ad_accounts_connection", columnList = "connection_id")
    })
public class AdAccount {

  @Id private Long id;

  @Column(name = "company_id", nullable = false)
  private Long companyId;

  @Column(name = "connection_id", nullable = false)
  private Long connectionId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private AdProvider provider;

  @Column(name = "external_account_id", nullable = false, length = 255)
  private String externalAccountId;

  @Column(nullable = false, length = 160)
  private String name;

  @Column(name = "external_business_id", length = 255)
  private String externalBusinessId;

  @Column(length = 8)
  private String currency;

  @Column(length = 80)
  private String timezone;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private AdAccountStatus status;

  @Column(name = "primary_account", nullable = false)
  private boolean primaryAccount;

  @Column(nullable = false)
  private boolean managed;

  @Column(name = "provider_payload_json", columnDefinition = "text")
  private String providerPayloadJson;

  @Column(name = "last_sync_at")
  private Instant lastSyncAt;

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
    if (status == null) status = AdAccountStatus.UNKNOWN;
    if (createdAt == null) createdAt = now;
    updatedAt = now;
  }

  @PreUpdate
  void beforeUpdate() {
    updatedAt = Instant.now();
  }
}
