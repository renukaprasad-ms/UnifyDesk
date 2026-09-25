package com.messaging.ads.entity;

import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.enums.AdRecommendationLifecycleStatus;
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
    name = "ad_recommendation_states",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_ad_recommendation_state",
          columnNames = {"ad_account_id", "provider", "recommendation_key"})
    },
    indexes = {
      @Index(
          name = "idx_ad_recommendation_state_company_status",
          columnList = "company_id,status"),
      @Index(
          name = "idx_ad_recommendation_state_account_status",
          columnList = "ad_account_id,status")
    })
public class AdRecommendationState {

  @Id private Long id;

  @Column(name = "company_id", nullable = false)
  private Long companyId;

  @Column(name = "ad_account_id", nullable = false)
  private Long adAccountId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private AdProvider provider;

  @Column(name = "recommendation_key", nullable = false, length = 255)
  private String recommendationKey;

  @Column(nullable = false, length = 128)
  private String fingerprint;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private AdRecommendationLifecycleStatus status =
      AdRecommendationLifecycleStatus.ACTIVE;

  @Column(name = "snoozed_until")
  private Instant snoozedUntil;

  @Column(name = "actor_user_id")
  private Long actorUserId;

  @Column(name = "last_seen_at", nullable = false)
  private Instant lastSeenAt;

  @Column(name = "resolved_at")
  private Instant resolvedAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(nullable = false)
  private long version;

  @PrePersist
  void beforeInsert() {
    if (id == null) id = IdGenerator.nextId();
    Instant now = Instant.now();
    if (createdAt == null) createdAt = now;
    if (lastSeenAt == null) lastSeenAt = now;
    updatedAt = now;
  }

  @PreUpdate
  void beforeUpdate() {
    updatedAt = Instant.now();
  }
}
