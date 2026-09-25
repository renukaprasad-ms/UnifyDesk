package com.messaging.ads.entity;

import com.messaging.ads.enums.AdAccountResourceType;
import com.messaging.ads.enums.AdProvider;
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
    name = "ad_account_resources",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_ad_account_resource_external",
          columnNames = {"ad_account_id", "resource_type", "external_resource_id"})
    },
    indexes = {
      @Index(
          name = "idx_ad_account_resources_account_type",
          columnList = "ad_account_id,resource_type"),
      @Index(
          name = "idx_ad_account_resources_company_access",
          columnList = "company_id,accessible")
    })
public class AdAccountResource {

  @Id private Long id;

  @Column(name = "company_id", nullable = false)
  private Long companyId;

  @Column(name = "ad_account_id", nullable = false)
  private Long adAccountId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private AdProvider provider;

  @Enumerated(EnumType.STRING)
  @Column(name = "resource_type", nullable = false, length = 40)
  private AdAccountResourceType resourceType;

  @Column(name = "external_resource_id", nullable = false, length = 255)
  private String externalResourceId;

  @Column(length = 255)
  private String name;

  @Column(nullable = false)
  private boolean accessible = true;

  @Column(name = "provider_payload_json", columnDefinition = "text")
  private String providerPayloadJson;

  @Column(name = "last_sync_at")
  private Instant lastSyncAt;

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
