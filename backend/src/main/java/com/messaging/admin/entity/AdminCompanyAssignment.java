package com.messaging.admin.entity;

import com.messaging.common.id.IdGenerator;
import com.messaging.company.entity.Company;
import com.messaging.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
    name = "admin_company_assignments",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_admin_company_assignments_admin_company",
          columnNames = {"admin_user_id", "company_id"})
    },
    indexes = {
      @Index(name = "idx_admin_company_assignments_admin", columnList = "admin_user_id"),
      @Index(name = "idx_admin_company_assignments_company", columnList = "company_id")
    })
public class AdminCompanyAssignment {

  @Id private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "admin_user_id", nullable = false)
  private User adminUser;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "company_id", nullable = false)
  private Company company;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "assigned_by_user_id", nullable = false)
  private User assignedBy;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @PrePersist
  private void beforeInsert() {
    if (id == null) {
      id = IdGenerator.nextId();
    }
    if (createdAt == null) {
      createdAt = Instant.now();
    }
  }
}
