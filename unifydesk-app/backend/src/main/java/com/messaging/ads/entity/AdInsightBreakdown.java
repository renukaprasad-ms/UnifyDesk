package com.messaging.ads.entity;

import com.messaging.ads.enums.AdInsightBreakdownType;
import com.messaging.ads.enums.AdInsightLevel;
import com.messaging.ads.enums.AdProvider;
import com.messaging.common.id.IdGenerator;
import jakarta.persistence.*;
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
    name = "ad_insight_breakdowns",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_ad_insight_breakdown",
          columnNames = {
            "ad_account_id",
            "provider",
            "insight_level",
            "entity_ref",
            "breakdown_type",
            "dimension_value",
            "dimension_value_2",
            "action_type",
            "range_start",
            "range_end"
          })
    },
    indexes = {
      @Index(
          name = "idx_ad_insight_breakdowns_account_range",
          columnList = "ad_account_id,breakdown_type,range_start,range_end"),
      @Index(
          name = "idx_ad_insight_breakdowns_company_account",
          columnList = "company_id,ad_account_id")
    })
public class AdInsightBreakdown {

  @Id private Long id;

  @Column(name = "company_id", nullable = false)
  private Long companyId;

  @Column(name = "ad_account_id", nullable = false)
  private Long adAccountId;

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

  @Enumerated(EnumType.STRING)
  @Column(name = "breakdown_type", nullable = false, length = 40)
  private AdInsightBreakdownType breakdownType;

  @Column(name = "dimension_value", nullable = false, length = 255)
  private String dimensionValue;

  @Column(name = "dimension_value_2", nullable = false, length = 255)
  private String dimensionValue2 = "";

  @Column(name = "action_type", nullable = false, length = 255)
  private String actionType = "";

  @Column(name = "range_start", nullable = false)
  private LocalDate rangeStart;

  @Column(name = "range_end", nullable = false)
  private LocalDate rangeEnd;

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

  @Column(precision = 19, scale = 8)
  private BigDecimal ctr;

  @Column(precision = 19, scale = 8)
  private BigDecimal cpc;

  @Column(precision = 19, scale = 8)
  private BigDecimal cpm;

  @Column(nullable = false, precision = 19, scale = 6)
  private BigDecimal conversions = BigDecimal.ZERO;

  @Column(name = "conversion_value", nullable = false, precision = 19, scale = 6)
  private BigDecimal conversionValue = BigDecimal.ZERO;

  @Column(nullable = false, precision = 19, scale = 6)
  private BigDecimal leads = BigDecimal.ZERO;

  @Column(nullable = false, precision = 19, scale = 6)
  private BigDecimal purchases = BigDecimal.ZERO;

  @Column(name = "action_value", nullable = false, precision = 19, scale = 6)
  private BigDecimal actionValue = BigDecimal.ZERO;

  @Column(name = "actions_json", columnDefinition = "text")
  private String actionsJson;

  @Column(name = "action_values_json", columnDefinition = "text")
  private String actionValuesJson;

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
