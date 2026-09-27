package com.messaging.managedsms.control.entity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @NoArgsConstructor
@Entity
@Table(name = "managed_sms_template_headers")
public class ManagedSmsTemplateHeader {
  @EmbeddedId private ManagedSmsTemplateHeaderId id;
  @Column(name = "company_id", nullable = false) private Long companyId;
  @Column(name = "created_at", nullable = false) private Instant createdAt;
  @PrePersist void beforeInsert() { if (createdAt == null) createdAt = Instant.now(); }
}
