package com.messaging.ads.entity;

import com.messaging.ads.enums.AdProvider;
import com.messaging.common.id.IdGenerator;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
    name = "ad_account_health",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_ad_account_health",
          columnNames = {"ad_account_id", "provider"})
    },
    indexes = {
      @Index(
          name = "idx_ad_account_health_company",
          columnList = "company_id,provider")
    })
public class AdAccountHealth {

  @Id private Long id;

  @Column(name = "company_id", nullable = false)
  private Long companyId;

  @Column(name = "ad_account_id", nullable = false)
  private Long adAccountId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private AdProvider provider;

  @Column(name = "account_status")
  private Integer accountStatus;

  @Column(name = "disable_reason")
  private Integer disableReason;

  @Column(name = "opportunity_score", precision = 9, scale = 4)
  private BigDecimal opportunityScore;

  @Column(name = "opportunity_score_weight")
  private Integer opportunityScoreWeight;

  @Column(name = "page_authorized")
  private Boolean pageAuthorized;

  @Column(name = "offsite_pixels_tos_accepted")
  private Boolean offsitePixelsTosAccepted;

  @Column(name = "direct_deals_enabled")
  private Boolean directDealsEnabled;

  @Column(name = "notifications_enabled")
  private Boolean notificationsEnabled;

  @Column(name = "access_expires_at")
  private Instant accessExpiresAt;

  @Column(name = "offsite_clo_signal_status")
  private Integer offsiteCloSignalStatus;

  @Column(name = "capabilities_json", columnDefinition = "text")
  private String capabilitiesJson;

  @Column(name = "all_capabilities_json", columnDefinition = "text")
  private String allCapabilitiesJson;

  @Column(name = "user_tasks_json", columnDefinition = "text")
  private String userTasksJson;

  @Column(name = "failed_delivery_checks_json", columnDefinition = "text")
  private String failedDeliveryChecksJson;

  @Column(name = "provider_payload_json", columnDefinition = "text")
  private String providerPayloadJson;

  @Column(name = "last_sync_at", nullable = false)
  private Instant lastSyncAt;

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
    if (lastSyncAt == null) lastSyncAt = now;
    updatedAt = now;
  }

  @PreUpdate
  void beforeUpdate() {
    updatedAt = Instant.now();
  }
}
