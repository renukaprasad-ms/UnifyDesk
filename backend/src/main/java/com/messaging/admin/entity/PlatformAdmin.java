package com.messaging.admin.entity;

import com.messaging.admin.enums.PlatformAdminRole;
import com.messaging.admin.enums.PlatformAdminStatus;
import com.messaging.common.id.IdGenerator;
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
    name = "platform_admins",
    uniqueConstraints = {@UniqueConstraint(name = "uk_platform_admins_user", columnNames = "user_id")},
    indexes = {@Index(name = "idx_platform_admins_role", columnList = "role")})
public class PlatformAdmin {

  @Id private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private PlatformAdminRole role;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private PlatformAdminStatus status;

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
