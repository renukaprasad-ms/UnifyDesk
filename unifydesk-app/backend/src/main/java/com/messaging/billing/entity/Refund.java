package com.messaging.billing.entity;

import com.messaging.billing.enums.RefundStatus;
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
    name = "refunds",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_refunds_company_idempotency",
            columnNames = {"company_id", "idempotency_key"}))
public class Refund {
  @Id private Long id;

  @Column(name = "company_id", nullable = false)
  private Long companyId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "payment_id", nullable = false)
  private Payment payment;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private RefundStatus status;

  @Column(nullable = false, precision = 19, scale = 6)
  private BigDecimal amount;

  @Column(nullable = false, length = 3)
  private String currency;

  @Column(name = "idempotency_key", nullable = false, length = 255)
  private String idempotencyKey;

  @Column(name = "provider_refund_id", length = 255)
  private String providerRefundId;

  @Column(name = "provider_status", length = 64)
  private String providerStatus;

  @Column(name = "retention_until", nullable = false, insertable = false, updatable = false)
  private Instant retentionUntil;

  @Column(name = "legal_hold", nullable = false)
  private boolean legalHold;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @PrePersist
  void prePersist() {
    if (id == null) id = IdGenerator.nextId();
    if (status == null) status = RefundStatus.CREATED;
    Instant now = Instant.now();
    if (createdAt == null) createdAt = now;
    updatedAt = now;
  }

  @PreUpdate
  void preUpdate() {
    updatedAt = Instant.now();
  }
}
