package com.messaging.ads.entity;

import com.messaging.ads.enums.AdEntityStatus;
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
    name = "ad_campaigns",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_ad_campaign_provider_external",
          columnNames = {"ad_account_id", "provider", "external_campaign_id"})
    },
    indexes = {
      @Index(
          name = "idx_ad_campaigns_company_account",
          columnList = "company_id,ad_account_id"),
      @Index(
          name = "idx_ad_campaigns_account_status",
          columnList = "ad_account_id,status,accessible")
    })
public class AdCampaign {

  @Id private Long id;

  @Column(name = "company_id", nullable = false)
  private Long companyId;

  @Column(name = "ad_account_id", nullable = false)
  private Long adAccountId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private AdProvider provider;

  @Column(name = "external_campaign_id", nullable = false, length = 255)
  private String externalCampaignId;

  @Column(nullable = false, length = 255)
  private String name;

  @Column(length = 80)
  private String objective;

  @Column(name = "buying_type", length = 40)
  private String buyingType;

  @Column(name = "bid_strategy", length = 80)
  private String bidStrategy;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private AdEntityStatus status;

  @Column(name = "provider_status", length = 40)
  private String providerStatus;

  @Column(name = "effective_status", length = 80)
  private String effectiveStatus;

  @Column(name = "daily_budget_minor")
  private Long dailyBudgetMinor;

  @Column(name = "lifetime_budget_minor")
  private Long lifetimeBudgetMinor;

  @Column(name = "spend_cap_minor")
  private Long spendCapMinor;

  @Column(name = "start_at")
  private Instant startAt;

  @Column(name = "end_at")
  private Instant endAt;

  @Column(name = "provider_created_at")
  private Instant providerCreatedAt;

  @Column(name = "provider_updated_at")
  private Instant providerUpdatedAt;

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
    if (status == null) status = AdEntityStatus.UNKNOWN;
    if (createdAt == null) createdAt = now;
    updatedAt = now;
  }

  @PreUpdate
  void beforeUpdate() {
    updatedAt = Instant.now();
  }
}
