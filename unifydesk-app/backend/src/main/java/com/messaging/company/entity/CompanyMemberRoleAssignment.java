package com.messaging.company.entity;

import com.messaging.common.id.IdGenerator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
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
    name = "company_member_role_assignments",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_company_member_role_assignment",
          columnNames = {"company_id", "user_id", "role_id"})
    },
    indexes = {
      @Index(
          name = "idx_company_member_role_assignments_member",
          columnList = "company_id,user_id"),
      @Index(name = "idx_company_member_role_assignments_role", columnList = "role_id"),
      @Index(name = "idx_company_member_role_assignments_expires", columnList = "expires_at")
    })
public class CompanyMemberRoleAssignment {

  @Id private Long id;

  @Column(name = "company_id", nullable = false)
  private Long companyId;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "role_id", nullable = false)
  private CompanyCustomRole role;

  @Column(name = "assigned_by_user_id", nullable = false)
  private Long assignedByUserId;

  @Column(name = "expires_at")
  private Instant expiresAt;

  @Column(name = "revoked_at")
  private Instant revokedAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @PrePersist
  private void beforeInsert() {
    if (id == null) {
      id = IdGenerator.nextId();
    }
    Instant now = Instant.now();
    if (createdAt == null) {
      createdAt = now;
    }
    if (updatedAt == null) {
      updatedAt = now;
    }
  }

  @PreUpdate
  private void beforeUpdate() {
    updatedAt = Instant.now();
  }
}
