package com.messaging.billing.entity;

import com.messaging.billing.enums.PaymentAttemptStatus;
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
    name = "payment_attempts",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_payment_attempts_payment_number",
            columnNames = {"payment_id", "attempt_no"}))
public class PaymentAttempt {
  @Id private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "payment_id", nullable = false)
  private Payment payment;

  @Column(name = "attempt_no", nullable = false)
  private int attemptNo;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private PaymentAttemptStatus status;

  @Column(nullable = false, precision = 19, scale = 6)
  private BigDecimal amount;

  @Column(nullable = false, length = 3)
  private String currency;

  @Column(name = "provider_payment_id", length = 255)
  private String providerPaymentId;

  @Column(name = "failure_category", length = 80)
  private String failureCategory;

  @Column(name = "provider_created_at")
  private Instant providerCreatedAt;

  @Column(name = "resolved_at")
  private Instant resolvedAt;

  @Column(name = "retention_until", nullable = false, insertable = false, updatable = false)
  private Instant retentionUntil;

  @Column(name = "legal_hold", nullable = false)
  private boolean legalHold;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @PrePersist
  void prePersist() {
    if (id == null) id = IdGenerator.nextId();
    if (status == null) status = PaymentAttemptStatus.CREATED;
    if (createdAt == null) createdAt = Instant.now();
  }
}
