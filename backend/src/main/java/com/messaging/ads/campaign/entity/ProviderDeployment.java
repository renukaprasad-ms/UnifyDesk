package com.messaging.ads.campaign.entity;

import com.messaging.ads.campaign.enums.ProviderDeploymentStatus;
import com.messaging.ads.enums.AdProvider;
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
    name = "provider_deployments",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_provider_deployment_channel_account",
          columnNames = {"campaign_channel_id", "ad_account_id"})
    },
    indexes = {
      @Index(
          name = "idx_provider_deployments_company_campaign",
          columnList = "company_id,reachlyo_campaign_id"),
      @Index(
          name = "idx_provider_deployments_account_status",
          columnList = "ad_account_id,deployment_status")
    })
public class ProviderDeployment {

  @Id private Long id;

  @Column(name = "company_id", nullable = false)
  private Long companyId;

  @Column(name = "reachlyo_campaign_id", nullable = false)
  private Long reachlyoCampaignId;

  @Column(name = "campaign_channel_id", nullable = false)
  private Long campaignChannelId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private AdProvider provider;

  @Column(name = "ad_account_id", nullable = false)
  private Long adAccountId;

  @Column(name = "local_campaign_id")
  private Long localCampaignId;

  @Column(name = "external_campaign_id", length = 255)
  private String externalCampaignId;

  @Column(name = "provider_status", length = 80)
  private String providerStatus;

  @Enumerated(EnumType.STRING)
  @Column(name = "deployment_status", nullable = false, length = 32)
  private ProviderDeploymentStatus deploymentStatus;

  @Column(name = "last_error_code", length = 160)
  private String lastErrorCode;

  @Column(name = "last_error_message", length = 2000)
  private String lastErrorMessage;

  @Column(name = "last_sync_at")
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
    if (deploymentStatus == null) deploymentStatus = ProviderDeploymentStatus.PENDING;
    if (createdAt == null) createdAt = now;
    updatedAt = now;
  }

  @PreUpdate
  void beforeUpdate() {
    updatedAt = Instant.now();
  }
}
