package com.messaging.billing.entity;

import com.messaging.billing.enums.PaymentPurpose;
import com.messaging.billing.enums.PaymentStatus;
import com.messaging.common.id.IdGenerator;
import com.messaging.company.entity.Company;
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
    name = "payments",
    uniqueConstraints = {
      @UniqueConstraint(name = "uk_payments_invoice", columnNames = "invoice_id"),
      @UniqueConstraint(
          name = "uk_payments_company_idempotency",
          columnNames = {"company_id", "idempotency_key"})
    })
public class Payment {
  @Id private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "company_id", nullable = false)
  private Company company;

  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "invoice_id", nullable = false)
  private Invoice invoice;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private PaymentPurpose purpose;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private PaymentStatus status;

  @Column(nullable = false, precision = 19, scale = 6)
  private BigDecimal amount;

  @Column(nullable = false, length = 3)
  private String currency;

  @Column(name = "idempotency_key", nullable = false, length = 255)
  private String idempotencyKey;

  @Column(name = "provider", nullable = false, length = 32)
  private String provider;

  @Column(name = "provider_order_id", length = 255)
  private String providerOrderId;

  @Column(name = "provider_subscription_id", length = 255)
  private String providerSubscriptionId;

  @Column(name = "provider_order_state", nullable = false, length = 32)
  private String providerOrderState = "CREATE_PENDING";

  @Column(name = "provider_order_status", length = 64)
  private String providerOrderStatus;

  @Column(name = "provider_checkout_session_id", length = 2048)
  private String providerCheckoutSessionId;

  @Column(name = "lifecycle_applied_at")
  private Instant lifecycleAppliedAt;

  @Column(name = "checkout_intent_id")
  private Long checkoutIntentId;

  @Column(name = "subscription_change_id")
  private Long subscriptionChangeId;

  @Column(name = "subscription_id")
  private Long subscriptionId;

  @Column(name = "succeeded_at")
  private Instant succeededAt;

  @Column(name = "retention_until", nullable = false, insertable = false, updatable = false)
  private Instant retentionUntil;

  @Column(name = "legal_hold", nullable = false)
  private boolean legalHold;

  @Version
  @Column(nullable = false)
  private long version;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @PrePersist
  void prePersist() {
    if (id == null) id = IdGenerator.nextId();
    if (status == null) status = PaymentStatus.CREATED;
    Instant now = Instant.now();
    if (createdAt == null) createdAt = now;
    updatedAt = now;
  }

  @PreUpdate
  void preUpdate() {
    updatedAt = Instant.now();
  }
}
