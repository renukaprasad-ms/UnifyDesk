package com.messaging.managedemail.control.entity;

import com.messaging.common.id.IdGenerator;
import com.messaging.managedemail.control.enums.ManagedEmailEnums.*;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "managed_email_provisioning_operations")
public class ManagedEmailProvisioningOperation {

  @Id private Long id;

  @Column(name = "company_id", nullable = false)
  private Long companyId;

  @Enumerated(EnumType.STRING)
  @Column(name = "resource_type", nullable = false, length = 32)
  private ProvisioningResourceType resourceType;

  @Column(name = "resource_id", nullable = false)
  private Long resourceId;

  @Enumerated(EnumType.STRING)
  @Column(name = "operation_type", nullable = false, length = 48)
  private ProvisioningOperationType operationType;

  @Column(name = "idempotency_key", nullable = false, unique = true, length = 255)
  private String idempotencyKey;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private ProvisioningOperationStatus status;

  @Column(length = 64)
  private String checkpoint;

  @Column(name = "attempt_count", nullable = false)
  private int attemptCount;

  @Column(name = "next_attempt_at")
  private Instant nextAttemptAt;

  @Column(name = "claimed_at")
  private Instant claimedAt;

  @Column(name = "last_error_code", length = 128)
  private String lastErrorCode;

  @Column(name = "last_error_message", length = 1000)
  private String lastErrorMessage;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "completed_at")
  private Instant completedAt;

  @PrePersist
  void beforeInsert() {
    if (id == null) id = IdGenerator.nextId();
    if (status == null) status = ProvisioningOperationStatus.PENDING;
    Instant now = Instant.now();
    if (createdAt == null) createdAt = now;
    updatedAt = now;
  }

  @PreUpdate
  void beforeUpdate() {
    updatedAt = Instant.now();
  }
}
