package com.messaging.notification.platform.core.entity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "notification_events")
@Getter
@Setter
@NoArgsConstructor
public class NotificationEvent {
  @Id
  @Column(name = "id")
  private Long id;

  @Column(name = "event_key", length = 255)
  private String eventKey;

  @Column(name = "event_type", length = 100)
  private String eventType;

  @Column(name = "schema_version")
  private int schemaVersion;

  @Column(name = "company_id")
  private Long companyId;

  @Column(name = "actor_user_id")
  private Long actorUserId;

  @Column(name = "recipient_mode", length = 32)
  private String recipientMode;

  @Column(name = "recipient_user_id")
  private Long recipientUserId;

  @Column(name = "required_permission_key", length = 160)
  private String requiredPermissionKey;

  @Column(name = "subject_type", length = 64)
  private String subjectType;

  @Column(name = "subject_id", length = 160)
  private String subjectId;

  @Column(name = "priority", length = 16)
  private String priority;

  @Column(name = "group_key", length = 180)
  private String groupKey;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "variables_json", columnDefinition = "jsonb")
  private String variablesJson;

  @Column(name = "correlation_id", length = 80)
  private String correlationId;

  @Column(name = "causation_id", length = 80)
  private String causationId;

  @Column(name = "occurred_at")
  private Instant occurredAt;

  @Column(name = "publish_state", length = 24)
  private String publishState;

  @Column(name = "publish_attempts")
  private int publishAttempts;

  @Column(name = "next_publish_at")
  private Instant nextPublishAt;

  @Column(name = "claim_token", length = 80)
  private String claimToken;

  @Column(name = "claim_expires_at")
  private Instant claimExpiresAt;

  @Column(name = "published_at")
  private Instant publishedAt;

  @Column(name = "materialized_at")
  private Instant materializedAt;

  @Column(name = "last_error_code", length = 80)
  private String lastErrorCode;

  @Column(name = "created_at")
  private Instant createdAt;

  @Column(name = "updated_at")
  private Instant updatedAt;
}
