package com.messaging.channel.meta.control;

import com.messaging.common.id.IdGenerator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "meta_whatsapp_provisioning_operations")
public class MetaWhatsAppProvisioningOperation {

  @Id private Long id;

  @Column(name = "connection_id", nullable = false)
  private Long connectionId;

  @Column(name = "company_id", nullable = false)
  private Long companyId;

  @Column(name = "operation_key", nullable = false, unique = true, length = 255)
  private String operationKey;

  @Enumerated(EnumType.STRING)
  @Column(name = "operation_type", nullable = false, length = 40)
  private OperationType operationType;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private OperationState state;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 40)
  private ProvisioningStep step;

  @Column(name = "attempt_count", nullable = false)
  private int attemptCount;

  @Column(name = "next_attempt_at")
  private Instant nextAttemptAt;

  @Column(name = "claim_token", length = 80)
  private String claimToken;

  @Column(name = "claim_expires_at")
  private Instant claimExpiresAt;

  @Column(name = "last_safe_error_code", length = 80)
  private String lastSafeErrorCode;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "completed_at")
  private Instant completedAt;

  @PrePersist
  void beforeInsert() {
    if (id == null) {
      id = IdGenerator.nextId();
    }
    Instant now = Instant.now();
    if (state == null) {
      state = OperationState.PENDING;
    }
    if (step == null) {
      step = ProvisioningStep.LOCAL_PERSISTED;
    }
    if (createdAt == null) {
      createdAt = now;
    }
    if (updatedAt == null) {
      updatedAt = now;
    }
  }

  @PreUpdate
  void beforeUpdate() {
    updatedAt = Instant.now();
  }

  public enum OperationType {
    CONNECT,
    REAUTHORIZE,
    RECONCILE
  }

  public enum OperationState {
    PENDING,
    CLAIMED,
    RUNNING,
    SUCCEEDED,
    FAILED_RETRYABLE,
    FAILED_PERMANENT
  }

  public enum ProvisioningStep {
    TOKEN_VALIDATED,
    ASSETS_VALIDATED,
    LOCAL_PERSISTED,
    PHONE_REGISTERED,
    APP_SUBSCRIBED,
    HEALTH_VALIDATED
  }
}
