package com.messaging.emailtemplate.entity;

import com.messaging.common.id.IdGenerator;
import com.messaging.emailtemplate.enums.EmailTemplateCategory;
import com.messaging.emailtemplate.enums.EmailTemplateStatus;
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
    name = "email_templates",
    indexes = {
      @Index(name = "idx_email_templates_company_status_updated", columnList = "company_id,status,updated_at"),
      @Index(name = "idx_email_templates_company_name", columnList = "company_id,name")
    })
public class EmailTemplate {

  @Id private Long id;

  @Column(name = "company_id", nullable = false)
  private Long companyId;

  @Column(nullable = false, length = 160)
  private String name;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private EmailTemplateCategory category;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private EmailTemplateStatus status;

  @Column(name = "current_version_id")
  private Long currentVersionId;

  @Column(name = "published_version_id")
  private Long publishedVersionId;

  @Column(name = "created_by_user_id", nullable = false)
  private Long createdByUserId;

  @Version
  @Column(nullable = false)
  private long version;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "archived_at")
  private Instant archivedAt;

  @PrePersist
  void beforeInsert() {
    if (id == null) id = IdGenerator.nextId();
    if (status == null) status = EmailTemplateStatus.DRAFT;
    Instant now = Instant.now();
    if (createdAt == null) createdAt = now;
    updatedAt = now;
  }

  @PreUpdate
  void beforeUpdate() {
    updatedAt = Instant.now();
  }
}
