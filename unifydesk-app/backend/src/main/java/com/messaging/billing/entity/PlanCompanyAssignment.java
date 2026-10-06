package com.messaging.billing.entity;

import com.messaging.common.id.IdGenerator;
import com.messaging.company.entity.Company;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
    name = "plan_company_assignments",
    indexes = {
      @Index(
          name = "idx_plan_company_assignments_lookup",
          columnList = "company_id,plan_id,effective_from,effective_to,revoked_at")
    })
public class PlanCompanyAssignment {
  @Id private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "plan_id", nullable = false)
  private Plan plan;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "company_id", nullable = false)
  private Company company;

  @Column(name = "assigned_at", nullable = false)
  private Instant assignedAt;

  @Column(name = "assigned_by_user_id", nullable = false)
  private Long assignedByUserId;

  @Column(name = "effective_from", nullable = false)
  private Instant effectiveFrom;

  @Column(name = "effective_to")
  private Instant effectiveTo;

  @Column(name = "revoked_at")
  private Instant revokedAt;

  @Column(name = "revoked_by_user_id")
  private Long revokedByUserId;

  @Column(length = 500)
  private String reason;

  @PrePersist
  private void beforeInsert() {
    if (id == null) id = IdGenerator.nextId();
    Instant now = Instant.now();
    if (assignedAt == null) assignedAt = now;
    if (effectiveFrom == null) effectiveFrom = assignedAt;
  }
}
