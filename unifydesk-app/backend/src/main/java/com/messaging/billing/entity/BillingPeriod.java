package com.messaging.billing.entity;

import com.messaging.billing.enums.BillingCycle;
import com.messaging.billing.enums.BillingPeriodKind;
import com.messaging.billing.enums.BillingPeriodStatus;
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
    name = "billing_periods",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_billing_period_company_kind_range",
          columnNames = {"company_id", "kind", "start_at", "end_at"})
    },
    indexes = {
      @Index(name = "idx_billing_period_due", columnList = "status,end_at"),
      @Index(name = "idx_billing_period_company", columnList = "company_id,kind,start_at"),
      @Index(name = "idx_billing_period_closing", columnList = "status,end_at,id")
    })
public class BillingPeriod {
  @Id private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "company_id", nullable = false)
  private Company company;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private BillingPeriodKind kind;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private BillingCycle cadence;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "subscription_id")
  private CompanySubscription subscription;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "plan_id")
  private Plan plan;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "plan_price_id")
  private PlanPrice planPrice;

  @Column(name = "platform_amount_snapshot", precision = 19, scale = 6)
  private BigDecimal platformAmountSnapshot;

  @Column(name = "currency_snapshot", length = 3)
  private String currencySnapshot;

  @Column(name = "start_at", nullable = false)
  private Instant startAt;

  @Column(name = "end_at", nullable = false)
  private Instant endAt;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private BillingPeriodStatus status;

  @Column(name = "finalized_at")
  private Instant finalizedAt;

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
    if (status == null) status = BillingPeriodStatus.OPEN;
    if (createdAt == null) createdAt = now;
    if (updatedAt == null) updatedAt = now;
  }

  @PreUpdate
  private void beforeUpdate() {
    updatedAt = Instant.now();
  }
}
