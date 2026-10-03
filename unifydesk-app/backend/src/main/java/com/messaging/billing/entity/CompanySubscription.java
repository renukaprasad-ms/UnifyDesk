package com.messaging.billing.entity;

import com.messaging.billing.enums.PaymentPolicy;
import com.messaging.billing.enums.SubscriptionStatus;
import com.messaging.common.id.IdGenerator;
import com.messaging.company.entity.Company;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
@Table(
    name = "company_subscriptions",
    indexes = {
      @Index(name = "idx_company_subscriptions_company_status", columnList = "company_id,status"),
      @Index(name = "idx_company_subscriptions_plan_id", columnList = "plan_id")
    })
public class CompanySubscription {

  @Id private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "company_id", nullable = false)
  private Company company;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "plan_id", nullable = false)
  private Plan plan;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "plan_price_id")
  private PlanPrice planPrice;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private SubscriptionStatus status;

  @Column(name = "started_at", nullable = false)
  private Instant startedAt;

  @Column(name = "ends_at")
  private Instant endsAt;

  @Column(name = "current_period_start")
  private Instant currentPeriodStart;

  @Column(name = "current_period_end")
  private Instant currentPeriodEnd;

  @Column(name = "billing_anchor")
  private Instant billingAnchor;

  @Column(name = "trial_ends_at")
  private Instant trialEndsAt;

  @Column(name = "grace_ends_at")
  private Instant graceEndsAt;

  @Column(name = "cancel_at_period_end", nullable = false)
  private boolean cancelAtPeriodEnd;

  @Column(name = "cancelled_at")
  private Instant cancelledAt;

  @Column(name = "cancellation_effective_at")
  private Instant cancellationEffectiveAt;

  @Enumerated(EnumType.STRING)
  @Column(name = "payment_policy", length = 32)
  private PaymentPolicy paymentPolicy;

  @Column(name = "external_contract_reference", length = 255)
  private String externalContractReference;

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
    if (startedAt == null) startedAt = now;
    if (currentPeriodStart == null) currentPeriodStart = startedAt;
    if (billingAnchor == null) billingAnchor = currentPeriodStart;
    if (createdAt == null) createdAt = now;
    if (updatedAt == null) updatedAt = now;
  }

  @PreUpdate
  private void beforeUpdate() {
    updatedAt = Instant.now();
  }
}
