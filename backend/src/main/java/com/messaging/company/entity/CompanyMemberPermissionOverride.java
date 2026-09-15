package com.messaging.company.entity;

import com.messaging.common.id.IdGenerator;
import com.messaging.company.enums.CompanyPermission;
import com.messaging.company.enums.PermissionEffect;
import com.messaging.user.entity.User;
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

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
    name = "company_member_permission_overrides",
    indexes = {
      @Index(
          name = "idx_company_member_permission_overrides_lookup",
          columnList = "company_id,user_id,permission"),
      @Index(name = "idx_company_member_permission_overrides_expires", columnList = "expires_at")
    })
public class CompanyMemberPermissionOverride {

  @Id private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "company_id", nullable = false)
  private Company company;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 64)
  private CompanyPermission permission;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private PermissionEffect effect;

  @Column(length = 255)
  private String reason;

  @Column(name = "expires_at")
  private Instant expiresAt;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "created_by_user_id", nullable = false)
  private User createdBy;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "revoked_at")
  private Instant revokedAt;

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
