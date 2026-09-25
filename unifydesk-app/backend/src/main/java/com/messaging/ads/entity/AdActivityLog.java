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
    name = "ad_activity_logs",
    indexes = {
      @Index(
          name = "idx_ad_activity_company_created",
          columnList = "company_id,created_at"),
      @Index(
          name = "idx_ad_activity_account_created",
          columnList = "ad_account_id,created_at")
    })
public class AdActivityLog {

  @Id private Long id;

  @Column(name = "company_id", nullable = false)
  private Long companyId;

  @Column(name = "ad_account_id")
  private Long adAccountId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private AdProvider provider;

  @Column(name = "actor_user_id")
  private Long actorUserId;

  @Column(nullable = false, length = 120)
  private String action;

  @Column(name = "entity_type", nullable = false, length = 80)
  private String entityType;

  @Column(name = "entity_id", length = 255)
  private String entityId;

  @Column(name = "before_json", columnDefinition = "text")
  private String beforeJson;

  @Column(name = "after_json", columnDefinition = "text")
  private String afterJson;

  @Column(name = "metadata_json", columnDefinition = "text")
  private String metadataJson;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @PrePersist
  void beforeInsert() {
    if (id == null) id = IdGenerator.nextId();
    if (createdAt == null) createdAt = Instant.now();
  }
}
