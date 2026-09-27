package com.messaging.managedsms.control.entity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @NoArgsConstructor
@Entity
@Table(name = "managed_sms_sender_bindings")
public class ManagedSmsSenderBinding {
  @Id @Column(name = "sender_id") private Long senderId;
  @Column(name = "company_id", nullable = false) private Long companyId;
  @Column(name = "tenant_id", nullable = false) private Long tenantId;
  @Column(name = "header_id", nullable = false) private Long headerId;
  @Column(name = "created_at", nullable = false) private Instant createdAt;
  @PrePersist void beforeInsert() { if (createdAt == null) createdAt = Instant.now(); }
}
