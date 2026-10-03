package com.messaging.billing.entity;

import com.messaging.billing.enums.SubscriptionChangeStatus;
import com.messaging.billing.enums.SubscriptionChangeType;
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
    name = "subscription_changes",
    uniqueConstraints = {
      @UniqueConstraint(name = "uk_subscription_changes_idempotency", columnNames = "idempotency_key")
    },
    indexes = {
      @Index(name = "idx_subscription_changes_due", columnList = "status,effective_at"),
      @Index(name = "idx_subscription_changes_company", columnList = "company_id,requested_at")
    })
public class SubscriptionChange {
  @Id private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "company_id", nullable = false)
  private Company company;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "subscription_id", nullable = false)
  private CompanySubscription subscription;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "from_plan_id", nullable = false)
  private Plan fromPlan;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "to_plan_id", nullable = false)
  private Plan toPlan;

  @Enumerated(EnumType.STRING)
  @Column(name = "change_type", nullable = false, length = 32)
  private SubscriptionChangeType changeType;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private SubscriptionChangeStatus status;

  @Column(name = "requested_at", nullable = false)
  private Instant requestedAt;

  @Column(name = "requested_by_user_id")
  private Long requestedByUserId;

  @Column(name = "effective_at", nullable = false)
  private Instant effectiveAt;

  @Column(name = "quote_reference", length = 160)
  private String quoteReference;

  @Column(name = "invoice_id")
  private Long invoiceId;

  @Column(name = "idempotency_key", nullable = false, length = 255)
  private String idempotencyKey;

  @Column(nullable = false, length = 3)
  private String currency;

  @Column(name = "current_price_snapshot", precision = 19, scale = 6)
  private BigDecimal currentPriceSnapshot;

  @Column(name = "target_price_snapshot", precision = 19, scale = 6)
  private BigDecimal targetPriceSnapshot;

  @Column(name = "current_period_start")
  private Instant currentPeriodStart;

  @Column(name = "current_period_end")
  private Instant currentPeriodEnd;

  @Column(name = "target_prorated_charge", precision = 19, scale = 6)
  private BigDecimal targetProratedCharge;

  @Column(name = "unused_current_credit", precision = 19, scale = 6)
  private BigDecimal unusedCurrentCredit;

  @Column(name = "net_amount", precision = 19, scale = 6)
  private BigDecimal netAmount;

  @Column(name = "amount_due", precision = 19, scale = 6)
  private BigDecimal amountDue;

  @Column(name = "credit_amount", precision = 19, scale = 6)
  private BigDecimal creditAmount;

  @Column(name = "applied_at")
  private Instant appliedAt;

  @Column(name = "cancelled_at")
  private Instant cancelledAt;

  @Column(name = "retention_until", nullable = false, insertable = false, updatable = false)
  private Instant retentionUntil;

  @Column(name = "legal_hold", nullable = false)
  private boolean legalHold;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @PrePersist
  private void beforeInsert() {
    if (id == null) id = IdGenerator.nextId();
    Instant now = Instant.now();
    if (requestedAt == null) requestedAt = now;
    if (createdAt == null) createdAt = now;
    if (updatedAt == null) updatedAt = now;
  }

  @PreUpdate
  private void beforeUpdate() {
    updatedAt = Instant.now();
  }
}
