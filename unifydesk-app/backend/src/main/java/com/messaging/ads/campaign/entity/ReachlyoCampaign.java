package com.messaging.ads.campaign.entity;

import com.messaging.ads.campaign.enums.CampaignBusinessGoal;
import com.messaging.ads.campaign.enums.ReachlyoCampaignObjective;
import com.messaging.ads.campaign.enums.ReachlyoCampaignStatus;
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
import jakarta.persistence.Version;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
    name = "reachlyo_campaigns",
    indexes = {
      @Index(name = "idx_reachlyo_campaigns_company_status", columnList = "company_id,status"),
      @Index(name = "idx_reachlyo_campaigns_company_updated", columnList = "company_id,updated_at")
    })
public class ReachlyoCampaign {

  @Id private Long id;

  @Column(name = "company_id", nullable = false)
  private Long companyId;

  @Column(nullable = false, length = 255)
  private String name;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 40)
  private ReachlyoCampaignObjective objective;

  @Enumerated(EnumType.STRING)
  @Column(name = "business_goal", nullable = false, length = 40)
  private CampaignBusinessGoal businessGoal;

  @Column(name = "landing_page_url", length = 2048)
  private String landingPageUrl;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private ReachlyoCampaignStatus status;

  @Column(nullable = false, length = 8)
  private String currency;

  @Column(name = "start_at")
  private Instant startAt;

  @Column(name = "end_at")
  private Instant endAt;

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
    if (status == null) status = ReachlyoCampaignStatus.DRAFT;
    if (createdAt == null) createdAt = now;
    updatedAt = now;
  }

  @PreUpdate
  void beforeUpdate() {
    updatedAt = Instant.now();
  }
}
