package com.messaging.ads.campaign.entity;

import com.messaging.ads.campaign.enums.AudienceMappingStatus;
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
    name = "audience_mappings",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_audience_mapping_definition_channel",
          columnNames = {"audience_definition_id", "campaign_channel_id"})
    },
    indexes = {
      @Index(name = "idx_audience_mappings_definition", columnList = "audience_definition_id"),
      @Index(name = "idx_audience_mappings_channel", columnList = "campaign_channel_id")
    })
public class AudienceMapping {

  @Id private Long id;

  @Column(name = "company_id", nullable = false)
  private Long companyId;

  @Column(name = "reachlyo_campaign_id", nullable = false)
  private Long reachlyoCampaignId;

  @Column(name = "audience_definition_id", nullable = false)
  private Long audienceDefinitionId;

  @Column(name = "campaign_channel_id", nullable = false)
  private Long campaignChannelId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private AdProvider provider;

  @Enumerated(EnumType.STRING)
  @Column(name = "mapping_status", nullable = false, length = 24)
  private AudienceMappingStatus mappingStatus;

  @Column(name = "local_audience_id")
  private Long localAudienceId;

  @Column(name = "external_audience_id", length = 255)
  private String externalAudienceId;

  @Column(name = "provider_criteria_json", columnDefinition = "text")
  private String providerCriteriaJson;

  @Column(columnDefinition = "text")
  private String notes;

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
