package com.messaging.billing.entity;

import com.messaging.billing.enums.PaymentPolicy;
import com.messaging.billing.enums.SubscriptionStatus;
import com.messaging.billing.enums.SubscriptionTransitionType;
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
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Immutable;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Immutable
@Table(
    name = "subscription_transitions",
    indexes = {
      @Index(name = "idx_subscription_transitions_company_time", columnList = "company_id,occurred_at"),
      @Index(name = "idx_subscription_transitions_subscription", columnList = "subscription_id,occurred_at")
    })
public class SubscriptionTransition {
  @Id private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "company_id", nullable = false)
  private Company company;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "subscription_id", nullable = false)
  private CompanySubscription subscription;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "from_plan_id")
  private Plan fromPlan;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "to_plan_id")
  private Plan toPlan;

  @Enumerated(EnumType.STRING)
  @Column(name = "from_status", length = 32)
  private SubscriptionStatus fromStatus;

  @Enumerated(EnumType.STRING)
  @Column(name = "to_status", nullable = false, length = 32)
  private SubscriptionStatus toStatus;

  @Enumerated(EnumType.STRING)
  @Column(name = "transition_type", nullable = false, length = 64)
  private SubscriptionTransitionType transitionType;

  @Column(name = "actor_user_id")
  private Long actorUserId;

  @Column(length = 1000)
  private String reason;

  @Column(name = "effective_from")
  private Instant effectiveFrom;

  @Column(name = "effective_to")
  private Instant effectiveTo;

  @Enumerated(EnumType.STRING)
  @Column(name = "payment_policy", length = 32)
  private PaymentPolicy paymentPolicy;

  @Column(name = "external_reference", length = 255)
  private String externalReference;

  @Column(name = "occurred_at", nullable = false)
  private Instant occurredAt;

  @Column(name = "retention_until", nullable = false, insertable = false, updatable = false)
  private Instant retentionUntil;

  @Column(name = "legal_hold", nullable = false)
  private boolean legalHold;

  @PrePersist
  private void beforeInsert() {
    if (id == null) id = IdGenerator.nextId();
    if (occurredAt == null) occurredAt = Instant.now();
  }
}
