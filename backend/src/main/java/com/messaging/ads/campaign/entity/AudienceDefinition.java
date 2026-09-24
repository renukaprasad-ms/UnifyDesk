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
    name = "audience_definitions",
    indexes = {
      @Index(name = "idx_audience_definitions_company_campaign", columnList = "company_id,reachlyo_campaign_id")
    })
public class AudienceDefinition {

  @Id private Long id;

  @Column(name = "company_id", nullable = false)
  private Long companyId;

  @Column(name = "reachlyo_campaign_id", nullable = false)
  private Long reachlyoCampaignId;

  @Column(nullable = false, length = 255)
  private String name;

  @Column(columnDefinition = "text")
  private String description;

  @Column(name = "age_min")
  private Integer ageMin;

  @Column(name = "age_max")
  private Integer ageMax;

  @Column(name = "locations_json", columnDefinition = "text")
  private String locationsJson;

  @Column(name = "languages_json", columnDefinition = "text")
  private String languagesJson;

  @Column(name = "genders_json", columnDefinition = "text")
  private String gendersJson;

  @Column(name = "customer_segments_json", columnDefinition = "text")
  private String customerSegmentsJson;

  @Column(name = "retargeting_json", columnDefinition = "text")
  private String retargetingJson;

  @Column(name = "exclusions_json", columnDefinition = "text")
  private String exclusionsJson;

  @Column(name = "interests_json", columnDefinition = "text")
  private String interestsJson;

  @Column(name = "search_intent_json", columnDefinition = "text")
  private String searchIntentJson;

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
