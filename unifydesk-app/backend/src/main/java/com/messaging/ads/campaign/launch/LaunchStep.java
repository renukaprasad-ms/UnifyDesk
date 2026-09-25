package com.messaging.ads.campaign.launch;

import com.messaging.ads.campaign.compiler.ProviderPlanAction;
import com.messaging.ads.campaign.compiler.ProviderPlanResource;
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
    name = "campaign_launch_steps",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_campaign_launch_step_operation",
          columnNames = {"launch_run_id", "campaign_channel_id", "operation_key"})
    },
    indexes = {
      @Index(
          name = "idx_campaign_launch_steps_run_sequence",
          columnList = "launch_run_id,sequence_no"),
      @Index(
          name = "idx_campaign_launch_steps_status",
          columnList = "company_id,status,updated_at")
    })
public class LaunchStep {

  @Id private Long id;

  @Column(name = "company_id", nullable = false)
  private Long companyId;

  @Column(name = "reachlyo_campaign_id", nullable = false)
  private Long reachlyoCampaignId;

  @Column(name = "launch_run_id", nullable = false)
  private Long launchRunId;

  @Column(name = "campaign_channel_id", nullable = false)
  private Long campaignChannelId;

  @Column(name = "provider_deployment_id", nullable = false)
  private Long providerDeploymentId;

  @Column(name = "ad_account_id", nullable = false)
  private Long adAccountId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private AdProvider provider;

  @Column(name = "sequence_no", nullable = false)
  private int sequence;

  @Column(name = "operation_key", nullable = false, length = 160)
  private String operationKey;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private ProviderPlanAction action;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 40)
  private ProviderPlanResource resource;

  @Column(name = "create_paused", nullable = false)
  private boolean createPaused;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private LaunchStepStatus status;

  @Column(name = "attempt_count", nullable = false)
  private int attemptCount;

  @Column(name = "step_idempotency_key", nullable = false, length = 220)
  private String stepIdempotencyKey;

  @Column(name = "local_resource_id")
  private Long localResourceId;

  @Column(name = "external_resource_id", length = 255)
  private String externalResourceId;

  @Column(name = "request_json", columnDefinition = "text")
  private String requestJson;

  @Column(name = "response_json", columnDefinition = "text")
  private String responseJson;

  @Column(name = "last_error_code", length = 160)
  private String lastErrorCode;

  @Column(name = "last_error_message", length = 2000)
  private String lastErrorMessage;

  @Column(name = "started_at")
  private Instant startedAt;

  @Column(name = "completed_at")
  private Instant completedAt;

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
    if (status == null) status = LaunchStepStatus.PENDING;
    if (createdAt == null) createdAt = now;
    updatedAt = now;
  }

  @PreUpdate
  void beforeUpdate() {
    updatedAt = Instant.now();
  }
}
