package com.messaging.ads.campaign.entity;

import com.messaging.ads.campaign.enums.CampaignChannelStatus;
import com.messaging.ads.campaign.enums.CampaignChannelType;
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
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
    name = "campaign_channels",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_campaign_channel_type",
          columnNames = {"reachlyo_campaign_id", "channel_type"})
    },
    indexes = {
      @Index(
          name = "idx_campaign_channels_company_campaign",
          columnList = "company_id,reachlyo_campaign_id"),
      @Index(
          name = "idx_campaign_channels_provider_status",
          columnList = "provider,status")
    })
public class CampaignChannel {

  @Id private Long id;

  @Column(name = "company_id", nullable = false)
  private Long companyId;

  @Column(name = "reachlyo_campaign_id", nullable = false)
  private Long reachlyoCampaignId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private AdProvider provider;

  @Enumerated(EnumType.STRING)
  @Column(name = "channel_type", nullable = false, length = 48)
  private CampaignChannelType channelType;

  @Column(nullable = false)
  private boolean enabled = true;

  @Column(name = "budget_allocation_minor")
  private Long budgetAllocationMinor;

  @Column(name = "allocation_percent", precision = 5, scale = 2)
  private BigDecimal allocationPercent;

  @Column(name = "provider_override_json", columnDefinition = "text")
  private String providerOverrideJson;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private CampaignChannelStatus status;

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
    if (status == null) status = enabled ? CampaignChannelStatus.DRAFT : CampaignChannelStatus.DISABLED;
    if (createdAt == null) createdAt = now;
    updatedAt = now;
  }

  @PreUpdate
  void beforeUpdate() {
    updatedAt = Instant.now();
  }
}
