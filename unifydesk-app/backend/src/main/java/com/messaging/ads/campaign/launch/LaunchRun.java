package com.messaging.ads.campaign.launch;

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
    name = "campaign_launch_runs",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_campaign_launch_run_idempotency",
          columnNames = {"company_id", "reachlyo_campaign_id", "idempotency_key"})
    },
    indexes = {
      @Index(
          name = "idx_campaign_launch_runs_company_campaign",
          columnList = "company_id,reachlyo_campaign_id,created_at"),
      @Index(
          name = "idx_campaign_launch_runs_status",
          columnList = "company_id,status,updated_at")
    })
public class LaunchRun {

  @Id private Long id;

  @Column(name = "company_id", nullable = false)
  private Long companyId;

  @Column(name = "reachlyo_campaign_id", nullable = false)
  private Long reachlyoCampaignId;

  @Column(name = "idempotency_key", nullable = false, length = 160)
  private String idempotencyKey;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private LaunchRunStatus status;

  @Column(name = "requested_by_user_id", nullable = false)
  private Long requestedByUserId;

  @Column(name = "plan_snapshot_json", columnDefinition = "text")
  private String planSnapshotJson;

  @Column(name = "validation_issues_json", columnDefinition = "text")
  private String validationIssuesJson;

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
    if (status == null) status = LaunchRunStatus.VALIDATING;
    if (createdAt == null) createdAt = now;
    updatedAt = now;
  }

  @PreUpdate
  void beforeUpdate() {
    updatedAt = Instant.now();
  }
}
