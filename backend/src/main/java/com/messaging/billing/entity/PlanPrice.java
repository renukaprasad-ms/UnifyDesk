package com.messaging.billing.entity;

import com.messaging.billing.enums.BillingCycle;
import com.messaging.billing.enums.PlanPriceStatus;
import com.messaging.common.id.IdGenerator;
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
    name = "plan_prices",
    indexes = {
      @Index(name = "idx_plan_prices_plan_cycle_status", columnList = "plan_id,billing_cycle,status"),
      @Index(name = "idx_plan_prices_effective", columnList = "effective_from,effective_to")
    })
public class PlanPrice {

  @Id private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "plan_id", nullable = false)
  private Plan plan;

  @Enumerated(EnumType.STRING)
  @Column(name = "billing_cycle", nullable = false, length = 32)
  private BillingCycle billingCycle;

  @Column(nullable = false, precision = 19, scale = 6)
  private BigDecimal amount;

  @Column(nullable = false, length = 3)
  private String currency;

  @Column(name = "effective_from", nullable = false)
  private Instant effectiveFrom;

  @Column(name = "effective_to")
  private Instant effectiveTo;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private PlanPriceStatus status;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @PrePersist
  private void beforeInsert() {
    if (id == null) id = IdGenerator.nextId();
    Instant now = Instant.now();
    if (effectiveFrom == null) effectiveFrom = now;
    if (createdAt == null) createdAt = now;
    if (updatedAt == null) updatedAt = now;
  }

  @PreUpdate
  private void beforeUpdate() {
    updatedAt = Instant.now();
  }
}
