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
    name = "company_custom_role_permissions",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_company_custom_role_permission",
          columnNames = {"role_id", "permission_key"})
    },
    indexes = {
      @Index(name = "idx_company_custom_role_permissions_role", columnList = "role_id"),
      @Index(name = "idx_company_custom_role_permissions_key", columnList = "permission_key")
    })
public class CompanyCustomRolePermission {

  @Id private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "role_id", nullable = false)
  private CompanyCustomRole role;

  @Column(name = "permission_key", nullable = false, length = 120)
  private String permissionKey;

  @PrePersist
  private void beforeInsert() {
    if (id == null) {
      id = IdGenerator.nextId();
    }
  }
}
