package com.messaging.billing.entity;

import com.messaging.billing.enums.BillingCycle;
import com.messaging.billing.enums.PlanStatus;
import com.messaging.billing.enums.PlanVisibility;
import com.messaging.common.id.IdGenerator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
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
    name = "plans",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_plans_code_version",
          columnNames = {"code", "version"})
    },
    indexes = {@Index(name = "idx_plans_code_status", columnList = "code,status")})
public class Plan {

  @Id private Long id;

  @Column(nullable = false, length = 64)
  private String code;

  @Column(nullable = false, length = 120)
  private String name;

  @Column(length = 500)
  private String description;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private PlanStatus status;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private PlanVisibility visibility;

  @Enumerated(EnumType.STRING)
  @Column(name = "billing_cycle", nullable = false, length = 32)
  private BillingCycle billingCycle;

  @Column(name = "base_price", nullable = false, precision = 19, scale = 6)
  private BigDecimal basePrice;

  @Column(nullable = false, length = 3)
  private String currency;

  @Column(nullable = false)
  private int version;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @PrePersist
  private void beforeInsert() {
    if (id == null) id = IdGenerator.nextId();
    if (visibility == null) visibility = PlanVisibility.PUBLIC;
    Instant now = Instant.now();
    if (createdAt == null) createdAt = now;
    if (updatedAt == null) updatedAt = now;
  }

  @PreUpdate
  private void beforeUpdate() {
    updatedAt = Instant.now();
  }
}
