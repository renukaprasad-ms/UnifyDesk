package com.messaging.ads.campaign.entity;

import com.messaging.ads.campaign.enums.CreativeAssetRole;
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
    name = "creative_pack_assets",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_creative_pack_asset_media_role",
          columnNames = {"creative_pack_id", "media_id", "role"})
    },
    indexes = {
      @Index(name = "idx_creative_pack_assets_pack", columnList = "creative_pack_id"),
      @Index(name = "idx_creative_pack_assets_media", columnList = "media_id")
    })
public class CreativePackAsset {

  @Id private Long id;

  @Column(name = "company_id", nullable = false)
  private Long companyId;

  @Column(name = "creative_pack_id", nullable = false)
  private Long creativePackId;

  @Column(name = "media_id", nullable = false)
  private Long mediaId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private CreativeAssetRole role;

  @Column(length = 160)
  private String label;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @PrePersist
  void beforeInsert() {
    if (id == null) id = IdGenerator.nextId();
    if (createdAt == null) createdAt = Instant.now();
  }
}
