package com.messaging.company.entity;

import com.messaging.common.id.IdGenerator;
import com.messaging.company.enums.CompanyPermission;
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
    name = "company_access_profile_permissions",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_company_access_profile_permissions",
          columnNames = {"profile_id", "permission"})
    },
    indexes = {@Index(name = "idx_company_access_profile_permissions_profile", columnList = "profile_id")})
public class CompanyAccessProfilePermission {

  @Id private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "profile_id", nullable = false)
  private CompanyAccessProfile profile;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 64)
  private CompanyPermission permission;

  @PrePersist
  private void assignId() {
    if (id == null) {
      id = IdGenerator.nextId();
    }
  }
}
