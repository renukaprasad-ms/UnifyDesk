package com.messaging.ads.campaign.entity;

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
    name = "creative_packs",
    indexes = {
      @Index(name = "idx_creative_packs_company_campaign", columnList = "company_id,reachlyo_campaign_id")
    })
public class CreativePack {

  @Id private Long id;

  @Column(name = "company_id", nullable = false)
  private Long companyId;

  @Column(name = "reachlyo_campaign_id", nullable = false)
  private Long reachlyoCampaignId;

  @Column(nullable = false, length = 255)
  private String name;

  @Column(name = "business_name", length = 255)
  private String businessName;

  @Column(name = "offer_text", columnDefinition = "text")
  private String offerText;

  @Column(name = "primary_message", columnDefinition = "text")
  private String primaryMessage;

  @Column(name = "call_to_action", length = 80)
  private String callToAction;

  @Column(name = "destination_url", length = 2048)
  private String destinationUrl;

  @Column(name = "headlines_json", columnDefinition = "text")
  private String headlinesJson;

  @Column(name = "descriptions_json", columnDefinition = "text")
  private String descriptionsJson;

  @Column(name = "created_by_user_id", nullable = false)
  private Long createdByUserId;

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
