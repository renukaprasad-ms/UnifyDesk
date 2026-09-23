package com.messaging.ads.entity;

import com.messaging.ads.enums.AdEntityStatus;
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
    name = "ads",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_ad_provider_external",
          columnNames = {"ad_account_id", "provider", "external_ad_id"})
    },
    indexes = {
      @Index(name = "idx_ads_company_ad_set", columnList = "company_id,ad_set_id"),
      @Index(name = "idx_ads_ad_set_status", columnList = "ad_set_id,status,accessible")
    })
public class Ad {
  @Id private Long id;
  @Column(name = "company_id", nullable = false) private Long companyId;
  @Column(name = "ad_account_id", nullable = false) private Long adAccountId;
  @Column(name = "campaign_id", nullable = false) private Long campaignId;
  @Column(name = "ad_set_id", nullable = false) private Long adSetId;
  @Column(name = "creative_id") private Long creativeId;
  @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24) private AdProvider provider;
  @Column(name = "external_ad_id", nullable = false, length = 255) private String externalAdId;
  @Column(name = "external_creative_id", length = 255) private String externalCreativeId;
  @Column(nullable = false, length = 255) private String name;
  @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24) private AdEntityStatus status;
  @Column(name = "provider_status", length = 40) private String providerStatus;
  @Column(name = "effective_status", length = 80) private String effectiveStatus;
  @Column(name = "tracking_specs_json", columnDefinition = "text") private String trackingSpecsJson;
  @Column(name = "conversion_specs_json", columnDefinition = "text") private String conversionSpecsJson;
  @Column(name = "provider_created_at") private Instant providerCreatedAt;
  @Column(name = "provider_updated_at") private Instant providerUpdatedAt;
  @Column(nullable = false) private boolean accessible = true;
  @Column(name = "provider_payload_json", columnDefinition = "text") private String providerPayloadJson;
  @Column(name = "last_sync_at") private Instant lastSyncAt;
  @Column(name = "created_at", nullable = false) private Instant createdAt;
  @Column(name = "updated_at", nullable = false) private Instant updatedAt;

  @PrePersist
  void beforeInsert() {
    if (id == null) id = IdGenerator.nextId();
    Instant now = Instant.now();
    if (status == null) status = AdEntityStatus.UNKNOWN;
    if (createdAt == null) createdAt = now;
    updatedAt = now;
  }

  @PreUpdate
  void beforeUpdate() { updatedAt = Instant.now(); }
}
