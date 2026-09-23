package com.messaging.ads.entity;

import com.messaging.ads.enums.AdInsightGranularity;
import com.messaging.ads.enums.AdInsightLevel;
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
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
    name = "ad_insights_daily",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_ad_insights_period",
          columnNames = {
            "ad_account_id",
            "provider",
            "insight_level",
            "granularity",
            "entity_ref",
            "insight_date",
            "period_end"
          })
    },
    indexes = {
      @Index(
          name = "idx_ad_insights_company_account_date",
          columnList = "company_id,ad_account_id,insight_date"),
      @Index(
          name = "idx_ad_insights_account_level_date",
          columnList = "ad_account_id,insight_level,insight_date")
    })
public class AdInsightDaily {

  @Id private Long id;

  @Column(name = "company_id", nullable = false)
  private Long companyId;

  @Column(name = "ad_account_id", nullable = false)
  private Long adAccountId;

  @Column(name = "campaign_id")
  private Long campaignId;

  @Column(name = "ad_set_id")
  private Long adSetId;

  @Column(name = "ad_id")
  private Long adId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private AdProvider provider;

  @Enumerated(EnumType.STRING)
  @Column(name = "insight_level", nullable = false, length = 24)
  private AdInsightLevel insightLevel;

  @Column(name = "entity_ref", nullable = false, length = 255)
  private String entityRef;

  @Column(name = "entity_name", length = 500)
  private String entityName;

  @Column(name = "insight_date", nullable = false)
  private LocalDate insightDate;

  @Column(name = "period_end", nullable = false)
  private LocalDate periodEnd;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private AdInsightGranularity granularity = AdInsightGranularity.DAILY;

  @Column(length = 12)
  private String currency;

  @Column(nullable = false, precision = 19, scale = 6)
  private BigDecimal spend = BigDecimal.ZERO;

  @Column(nullable = false)
  private long impressions;

  @Column(nullable = false)
  private long reach;

  @Column(nullable = false)
  private long clicks;

  @Column(name = "unique_clicks", nullable = false)
  private long uniqueClicks;

  @Column(name = "inline_link_clicks", nullable = false)
  private long inlineLinkClicks;

  @Column(precision = 19, scale = 8)
  private BigDecimal ctr;

  @Column(precision = 19, scale = 8)
  private BigDecimal cpc;

  @Column(precision = 19, scale = 8)
  private BigDecimal cpm;

  @Column(precision = 19, scale = 8)
  private BigDecimal frequency;

  @Column(nullable = false, precision = 19, scale = 6)
  private BigDecimal conversions = BigDecimal.ZERO;

  @Column(name = "conversion_value", nullable = false, precision = 19, scale = 6)
  private BigDecimal conversionValue = BigDecimal.ZERO;

  @Column(nullable = false, precision = 19, scale = 6)
  private BigDecimal leads = BigDecimal.ZERO;

  @Column(nullable = false, precision = 19, scale = 6)
  private BigDecimal purchases = BigDecimal.ZERO;

  @Column(nullable = false, precision = 19, scale = 6)
  private BigDecimal engagements = BigDecimal.ZERO;

  @Column(name = "video_views", nullable = false, precision = 19, scale = 6)
  private BigDecimal videoViews = BigDecimal.ZERO;

  @Column(precision = 19, scale = 8)
  private BigDecimal cpa;

  @Column(precision = 19, scale = 8)
  private BigDecimal roas;

  @Column(name = "actions_json", columnDefinition = "text")
  private String actionsJson;

  @Column(name = "action_values_json", columnDefinition = "text")
  private String actionValuesJson;

  @Column(name = "conversions_json", columnDefinition = "text")
  private String conversionsJson;

  @Column(name = "conversion_values_json", columnDefinition = "text")
  private String conversionValuesJson;

  @Column(name = "purchase_roas_json", columnDefinition = "text")
  private String purchaseRoasJson;

  @Column(name = "provider_metrics_json", columnDefinition = "text")
  private String providerMetricsJson;

  @Column(name = "last_sync_at", nullable = false)
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
    if (lastSyncAt == null) lastSyncAt = now;
    updatedAt = now;
  }

  @PreUpdate
  void beforeUpdate() {
    updatedAt = Instant.now();
  }
}
