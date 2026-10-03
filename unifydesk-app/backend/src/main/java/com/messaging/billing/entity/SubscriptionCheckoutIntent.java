package com.messaging.billing.entity;

import com.messaging.billing.enums.SubscriptionCheckoutIntentStatus;
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
    name = "subscription_checkout_intents",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_subscription_checkout_intents_company_idempotency",
          columnNames = {"company_id", "idempotency_key"}),
      @UniqueConstraint(
          name = "uk_subscription_checkout_intents_billing_period",
          columnNames = "billing_period_id"),
      @UniqueConstraint(
          name = "uk_subscription_checkout_intents_invoice",
          columnNames = "invoice_id")
    },
    indexes = {
      @Index(
          name = "idx_subscription_checkout_intents_company_status",
          columnList = "company_id,status"),
      @Index(name = "idx_subscription_checkout_intents_invoice", columnList = "invoice_id")
    })
public class SubscriptionCheckoutIntent {

  @Id private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "company_id", nullable = false)
  private Company company;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "plan_id", nullable = false)
  private Plan plan;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "plan_price_id", nullable = false)
  private PlanPrice planPrice;

  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "billing_period_id", nullable = false)
  private BillingPeriod billingPeriod;

  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "invoice_id")
  private Invoice invoice;

  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "subscription_id")
  private CompanySubscription subscription;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private SubscriptionCheckoutIntentStatus status;

  @Column(name = "idempotency_key", nullable = false, length = 255)
  private String idempotencyKey;

  @Column(nullable = false, length = 3)
  private String currency;

  @Column(name = "amount_snapshot", nullable = false, precision = 19, scale = 6)
  private BigDecimal amountSnapshot;

  @Column(name = "service_period_start", nullable = false)
  private Instant servicePeriodStart;

  @Column(name = "service_period_end", nullable = false)
  private Instant servicePeriodEnd;

  @Column(name = "requested_at", nullable = false)
  private Instant requestedAt;

  @Column(name = "requested_by_user_id")
  private Long requestedByUserId;

  @Column(name = "activated_at")
  private Instant activatedAt;

  @Column(name = "external_reference", length = 255)
  private String externalReference;

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
    if (status == null) status = SubscriptionCheckoutIntentStatus.PENDING_PAYMENT;
    if (requestedAt == null) requestedAt = now;
    if (createdAt == null) createdAt = now;
    if (updatedAt == null) updatedAt = now;
  }

  @PreUpdate
  private void beforeUpdate() {
    updatedAt = Instant.now();
  }
}
