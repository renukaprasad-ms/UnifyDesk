package com.messaging.company.entity;

import com.messaging.common.id.IdGenerator;
import com.messaging.company.enums.CompanyPermission;
import com.messaging.company.enums.CompanyRole;
import com.messaging.company.enums.PermissionEffect;
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
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
    name = "company_role_permissions",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_company_role_permissions_scope",
          columnNames = {"company_id", "role", "permission"})
    },
    indexes = {
      @Index(name = "idx_company_role_permissions_company", columnList = "company_id"),
      @Index(name = "idx_company_role_permissions_role", columnList = "role")
    })
public class CompanyRolePermission {

  @Id private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "company_id")
  private Company company;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private CompanyRole role;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 64)
  private CompanyPermission permission;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private PermissionEffect effect;

  @PrePersist
  private void assignId() {
    if (id == null) {
      id = IdGenerator.nextId();
    }
  }
}
