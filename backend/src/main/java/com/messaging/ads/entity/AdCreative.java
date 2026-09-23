package com.messaging.ads.entity;

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
    name = "ad_creatives",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_ad_creative_provider_external",
          columnNames = {"ad_account_id", "provider", "external_creative_id"})
    },
    indexes = {
      @Index(name = "idx_ad_creatives_company_account", columnList = "company_id,ad_account_id")
    })
public class AdCreative {
  @Id private Long id;
  @Column(name = "company_id", nullable = false) private Long companyId;
  @Column(name = "ad_account_id", nullable = false) private Long adAccountId;
  @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24) private AdProvider provider;
  @Column(name = "external_creative_id", nullable = false, length = 255) private String externalCreativeId;
  @Column(nullable = false, length = 255) private String name;
  @Column(name = "creative_format", length = 40) private String creativeFormat;
  @Column(name = "page_id", length = 255) private String pageId;
  @Column(name = "instagram_actor_id", length = 255) private String instagramActorId;
  @Column(name = "primary_text", columnDefinition = "text") private String primaryText;
  @Column(length = 500) private String headline;
  @Column(columnDefinition = "text") private String description;
  @Column(name = "call_to_action_type", length = 80) private String callToActionType;
  @Column(name = "destination_url", columnDefinition = "text") private String destinationUrl;
  @Column(name = "image_hash", length = 255) private String imageHash;
  @Column(name = "image_url", columnDefinition = "text") private String imageUrl;
  @Column(name = "video_id", length = 255) private String videoId;
  @Column(name = "url_tags", columnDefinition = "text") private String urlTags;
  @Column(name = "object_story_spec_json", columnDefinition = "text") private String objectStorySpecJson;
  @Column(name = "carousel_json", columnDefinition = "text") private String carouselJson;
  @Column(name = "provider_created_at") private Instant providerCreatedAt;
  @Column(name = "provider_updated_at") private Instant providerUpdatedAt;
  @Column(nullable = false) private boolean accessible = true;
  @Column(name = "provider_payload_json", columnDefinition = "text") private String providerPayloadJson;
  @Column(name = "last_sync_at") private Instant lastSyncAt;
  @Column(name = "created_at", nullable = false) private Instant createdAt;
  @Column(name = "updated_at", nullable = false) private Instant updatedAt;

  @PrePersist
  void beforeInsert() {
    if (id == null) id = IdGenerator.nextId();
    Instant now = Instant.now();
    if (createdAt == null) createdAt = now;
    updatedAt = now;
  }

  @PreUpdate
  void beforeUpdate() { updatedAt = Instant.now(); }
}
