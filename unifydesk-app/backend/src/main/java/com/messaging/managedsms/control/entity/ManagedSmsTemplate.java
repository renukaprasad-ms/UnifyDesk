package com.messaging.managedsms.control.entity;

import com.messaging.common.id.IdGenerator;
import com.messaging.managedsms.control.enums.ManagedSmsEnums.MessageCategory;
import com.messaging.managedsms.control.enums.ManagedSmsEnums.TemplateStatus;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @NoArgsConstructor
@Entity
@Table(name = "managed_sms_templates")
public class ManagedSmsTemplate {
  @Id private Long id;
  @Column(name = "company_id", nullable = false) private Long companyId;
  @Column(nullable = false, length = 160) private String name;
  @Enumerated(EnumType.STRING) @Column(name = "purpose", nullable = false, length = 32) private MessageCategory purpose;
  @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) private TemplateStatus status;
  @Column(name = "created_by_user_id", nullable = false) private Long createdByUserId;
  @Column(name = "archived_at") private Instant archivedAt;
  @Column(name = "created_at", nullable = false) private Instant createdAt;
  @Column(name = "updated_at", nullable = false) private Instant updatedAt;
  @Version @Column(name = "lock_version", nullable = false) private long lockVersion;

  @PrePersist void beforeInsert() {
    if (id == null) id = IdGenerator.nextId();
    if (status == null) status = TemplateStatus.DRAFT;
    Instant now = Instant.now();
    if (createdAt == null) createdAt = now;
    updatedAt = now;
  }
  @PreUpdate void beforeUpdate() { updatedAt = Instant.now(); }
}
