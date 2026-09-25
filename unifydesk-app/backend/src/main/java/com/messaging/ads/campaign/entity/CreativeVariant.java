package com.messaging.ads.campaign.entity;

import com.messaging.ads.campaign.enums.CreativeVariantOrigin;
import com.messaging.ads.campaign.enums.CreativeVariantStatus;
import com.messaging.ads.campaign.enums.CreativeVariantType;
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
    name = "creative_variants",
    indexes = {
      @Index(name = "idx_creative_variants_pack", columnList = "creative_pack_id"),
      @Index(name = "idx_creative_variants_channel", columnList = "campaign_channel_id"),
      @Index(name = "idx_creative_variants_provider_type", columnList = "provider,variant_type")
    })
public class CreativeVariant {

  @Id private Long id;

  @Column(name = "company_id", nullable = false)
  private Long companyId;

  @Column(name = "reachlyo_campaign_id", nullable = false)
  private Long reachlyoCampaignId;

  @Column(name = "creative_pack_id", nullable = false)
  private Long creativePackId;

  @Column(name = "campaign_channel_id")
  private Long campaignChannelId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private AdProvider provider;

  @Enumerated(EnumType.STRING)
  @Column(name = "variant_type", nullable = false, length = 48)
  private CreativeVariantType variantType;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private CreativeVariantOrigin origin;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private CreativeVariantStatus status;

  @Column(nullable = false, length = 255)
  private String name;

  @Column(length = 120)
  private String placement;

  @Column(name = "primary_text", columnDefinition = "text")
  private String primaryText;

  @Column(name = "headlines_json", columnDefinition = "text")
  private String headlinesJson;

  @Column(name = "descriptions_json", columnDefinition = "text")
  private String descriptionsJson;

  @Column(name = "call_to_action", length = 80)
  private String callToAction;

  @Column(name = "destination_url", length = 2048)
  private String destinationUrl;

  @Column(name = "asset_spec_json", columnDefinition = "text")
  private String assetSpecJson;

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
    if (origin == null) origin = CreativeVariantOrigin.USER;
    if (status == null) status = CreativeVariantStatus.DRAFT;
    if (createdAt == null) createdAt = now;
    updatedAt = now;
  }

  @PreUpdate
  void beforeUpdate() {
    updatedAt = Instant.now();
  }
}
