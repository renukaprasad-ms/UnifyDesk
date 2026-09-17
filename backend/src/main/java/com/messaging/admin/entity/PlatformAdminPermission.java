package com.messaging.admin.entity;

import com.messaging.admin.enums.PlatformPermission;
import com.messaging.common.id.IdGenerator;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
    name = "platform_admin_permissions",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_platform_admin_permission",
            columnNames = {"platform_admin_id", "permission"}),
    indexes =
        @Index(name = "idx_platform_admin_permission_admin", columnList = "platform_admin_id"))
public class PlatformAdminPermission {
  @Id private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "platform_admin_id", nullable = false)
  private PlatformAdmin platformAdmin;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 64)
  private PlatformPermission permission;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @PrePersist
  void beforeInsert() {
    if (id == null) id = IdGenerator.nextId();
    if (createdAt == null) createdAt = Instant.now();
  }
}
