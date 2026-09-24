package com.messaging.ads.campaign.entity;

import com.messaging.ads.campaign.enums.BudgetAllocationMode;
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
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
    name = "campaign_budget_plans",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_campaign_budget_plan_campaign",
          columnNames = {"reachlyo_campaign_id"})
    },
    indexes = {
      @Index(
          name = "idx_campaign_budget_plans_company_campaign",
          columnList = "company_id,reachlyo_campaign_id")
    })
public class BudgetPlan {

  @Id private Long id;

  @Column(name = "company_id", nullable = false)
  private Long companyId;

  @Column(name = "reachlyo_campaign_id", nullable = false)
  private Long reachlyoCampaignId;

  @Column(name = "total_budget_minor", nullable = false)
  private Long totalBudgetMinor;

  @Enumerated(EnumType.STRING)
  @Column(name = "allocation_mode", nullable = false, length = 24)
  private BudgetAllocationMode allocationMode;

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
