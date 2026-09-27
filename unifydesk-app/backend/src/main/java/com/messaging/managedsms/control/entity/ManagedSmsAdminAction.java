package com.messaging.managedsms.control.entity;

import com.messaging.common.id.IdGenerator;
import com.messaging.managedsms.control.enums.ManagedSmsEnums.AdminActionType;
import com.messaging.managedsms.control.enums.ManagedSmsEnums.ProvisioningResourceType;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @NoArgsConstructor
@Entity
@Table(name = "managed_sms_admin_actions")
public class ManagedSmsAdminAction {
  @Id private Long id;
  @Column(name = "company_id", nullable = false) private Long companyId;
  @Column(name = "actor_user_id", nullable = false) private Long actorUserId;
  @Enumerated(EnumType.STRING) @Column(name = "resource_type", nullable = false, length = 40) private ProvisioningResourceType resourceType;
  @Column(name = "resource_id", nullable = false) private Long resourceId;
  @Enumerated(EnumType.STRING) @Column(nullable = false, length = 40) private AdminActionType action;
  @Column(nullable = false, length = 500) private String reason;
  @Column(name = "before_state_json", nullable = false, columnDefinition = "text") private String beforeStateJson;
  @Column(name = "after_state_json", nullable = false, columnDefinition = "text") private String afterStateJson;
  @Column(name = "created_at", nullable = false) private Instant createdAt;
  @PrePersist void beforeInsert() {
    if (id == null) id = IdGenerator.nextId();
    if (createdAt == null) createdAt = Instant.now();
  }
}
