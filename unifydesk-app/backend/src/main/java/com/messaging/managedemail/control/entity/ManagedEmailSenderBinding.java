package com.messaging.managedemail.control.entity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "managed_email_sender_bindings")
public class ManagedEmailSenderBinding {

  @Id
  @Column(name = "sender_id")
  private Long senderId;

  @Column(name = "company_id", nullable = false)
  private Long companyId;

  @Column(name = "domain_id", nullable = false)
  private Long domainId;

  @Column(name = "tenant_id", nullable = false)
  private Long tenantId;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @PrePersist
  void beforeInsert() {
    Instant now = Instant.now();
    if (createdAt == null) createdAt = now;
    updatedAt = now;
  }

  @PreUpdate
  void beforeUpdate() {
    updatedAt = Instant.now();
  }
}
