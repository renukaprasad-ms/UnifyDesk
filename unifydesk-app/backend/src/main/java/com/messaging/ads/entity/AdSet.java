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
    name = "ad_sets",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_ad_set_provider_external",
          columnNames = {"ad_account_id", "provider", "external_ad_set_id"})
    },
    indexes = {
      @Index(name = "idx_ad_sets_company_campaign", columnList = "company_id,campaign_id"),
      @Index(name = "idx_ad_sets_campaign_status", columnList = "campaign_id,status,accessible")
    })
public class AdSet {
  @Id private Long id;

  @Column(name = "company_id", nullable = false)
  private Long companyId;

  @Column(name = "ad_account_id", nullable = false)
  private Long adAccountId;

  @Column(name = "campaign_id", nullable = false)
  private Long campaignId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private AdProvider provider;

  @Column(name = "external_ad_set_id", nullable = false, length = 255)
  private String externalAdSetId;

  @Column(nullable = false, length = 255)
  private String name;

  @Column(name = "optimization_goal", length = 80)
  private String optimizationGoal;

  @Column(name = "billing_event", length = 80)
  private String billingEvent;

  @Column(name = "bid_strategy", length = 80)
  private String bidStrategy;

  @Column(name = "destination_type", length = 80)
  private String destinationType;

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

  @Column(name = "bid_amount_minor")
  private Long bidAmountMinor;

  @Column(name = "start_at")
  private Instant startAt;

  @Column(name = "end_at")
  private Instant endAt;

  @Column(name = "targeting_json", columnDefinition = "text")
  private String targetingJson;

  @Column(name = "promoted_object_json", columnDefinition = "text")
  private String promotedObjectJson;

  @Column(name = "attribution_spec_json", columnDefinition = "text")
  private String attributionSpecJson;

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
  void beforeUpdate() { updatedAt = Instant.now(); }
}
