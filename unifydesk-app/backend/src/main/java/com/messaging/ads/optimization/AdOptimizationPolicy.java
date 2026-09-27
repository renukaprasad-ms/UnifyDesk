package com.messaging.ads.optimization;

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
    name = "ad_optimization_policies",
    indexes = {
      @Index(name = "idx_ad_optimization_policies_company", columnList = "company_id,policy_type,enabled"),
      @Index(name = "idx_ad_optimization_policies_scope", columnList = "company_id,ad_account_id,campaign_id")
    })
public class AdOptimizationPolicy {

  @Id private Long id;

  @Column(name = "company_id", nullable = false)
  private Long companyId;

  @Column(name = "policy_type", nullable = false, length = 32)
  private String policyType;

  @Column(nullable = false, length = 255)
  private String name;

  @Column(nullable = false)
  private boolean enabled = true;

  @Column(nullable = false, length = 32)
  private String mode;

  @Enumerated(EnumType.STRING)
  @Column(length = 24)
  private AdProvider provider;

  @Column(name = "ad_account_id")
  private Long adAccountId;

  @Column(name = "campaign_id")
  private Long campaignId;

  @Column(name = "config_json", nullable = false, columnDefinition = "text")
  private String configJson;

  @Column(nullable = false, length = 32)
  private String status = "ACTIVE";

  @Column(name = "last_evaluated_at")
  private Instant lastEvaluatedAt;

  @Column(name = "last_triggered_at")
  private Instant lastTriggeredAt;

  @Column(name = "last_result_json", columnDefinition = "text")
  private String lastResultJson;

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
