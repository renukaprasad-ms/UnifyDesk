package com.messaging.ads.campaign.entity;

import com.messaging.ads.campaign.enums.CampaignBusinessGoal;
import com.messaging.ads.campaign.enums.ConversionTargetType;
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
    name = "conversion_goal_mappings",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_conversion_goal_campaign_provider_account",
          columnNames = {"reachlyo_campaign_id", "provider", "ad_account_id", "business_goal"})
    },
    indexes = {
      @Index(name = "idx_conversion_goal_mappings_campaign", columnList = "company_id,reachlyo_campaign_id"),
      @Index(name = "idx_conversion_goal_mappings_account", columnList = "ad_account_id")
    })
public class ConversionGoalMapping {

  @Id private Long id;

  @Column(name = "company_id", nullable = false)
  private Long companyId;

  @Column(name = "reachlyo_campaign_id", nullable = false)
  private Long reachlyoCampaignId;

  @Enumerated(EnumType.STRING)
  @Column(name = "business_goal", nullable = false, length = 40)
  private CampaignBusinessGoal businessGoal;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private AdProvider provider;

  @Column(name = "ad_account_id", nullable = false)
  private Long adAccountId;

  @Enumerated(EnumType.STRING)
  @Column(name = "target_type", nullable = false, length = 32)
  private ConversionTargetType targetType;

  @Column(name = "ad_conversion_id")
  private Long adConversionId;

  @Column(name = "provider_event_name", length = 120)
  private String providerEventName;

  @Column(name = "external_conversion_id", length = 255)
  private String externalConversionId;

  @Column(nullable = false)
  private boolean validated;

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
    if (createdAt == null) createdAt = now;
    updatedAt = now;
  }

  @PreUpdate
  void beforeUpdate() {
    updatedAt = Instant.now();
  }
}
