package com.messaging.managedemail.control.entity;

import com.messaging.common.id.IdGenerator;
import com.messaging.managedemail.control.enums.ManagedEmailEnums.AdminActionType;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "managed_email_admin_actions")
public class ManagedEmailAdminAction {

  @Id private Long id;

  @Column(name = "company_id", nullable = false)
  private Long companyId;

  @Column(name = "actor_user_id", nullable = false)
  private Long actorUserId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private AdminActionType action;

  @Column(nullable = false, length = 500)
  private String reason;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @PrePersist
  void beforeInsert() {
    if (id == null) id = IdGenerator.nextId();
    if (createdAt == null) createdAt = Instant.now();
  }
}
