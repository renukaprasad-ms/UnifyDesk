package com.messaging.emailtemplate.entity;

import com.messaging.broadcast.enums.BroadcastSenderProvider;
import com.messaging.common.id.IdGenerator;
import com.messaging.emailtemplate.enums.EmailTemplateProviderBindingStatus;
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
    name = "email_template_provider_bindings",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_email_template_provider_binding_version_connection",
            columnNames = {"template_version_id", "connection_id", "provider"}),
    indexes = {
      @Index(
          name = "idx_email_template_provider_binding_company_template",
          columnList = "company_id,template_id"),
      @Index(
          name = "idx_email_template_provider_binding_connection",
          columnList = "connection_id,provider,status")
    })
public class EmailTemplateProviderBinding {

  @Id private Long id;

  @Column(name = "company_id", nullable = false)
  private Long companyId;

  @Column(name = "template_id", nullable = false)
  private Long templateId;

  @Column(name = "template_version_id", nullable = false)
  private Long templateVersionId;

  @Column(name = "template_version_number", nullable = false)
  private long templateVersionNumber;

  @Column(name = "connection_id", nullable = false)
  private Long connectionId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 40)
  private BroadcastSenderProvider provider;

  @Column(name = "provider_template_id", length = 255)
  private String providerTemplateId;

  @Column(name = "provider_template_slug", nullable = false, length = 160)
  private String providerTemplateSlug;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private EmailTemplateProviderBindingStatus status;

  @Column(name = "provider_status", length = 128)
  private String providerStatus;

  @Column(name = "error_code", length = 128)
  private String errorCode;

  @Column(name = "error_message", length = 1000)
  private String errorMessage;

  @Column(name = "content_hash", nullable = false, length = 64)
  private String contentHash;

  @Column(name = "synced_at")
  private Instant syncedAt;

  @Column(name = "last_checked_at")
  private Instant lastCheckedAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(nullable = false)
  private long rowVersion;

  @PrePersist
  void beforeInsert() {
    if (id == null) id = IdGenerator.nextId();
    if (status == null) status = EmailTemplateProviderBindingStatus.SYNCING;
    Instant now = Instant.now();
    if (createdAt == null) createdAt = now;
    updatedAt = now;
  }

  @PreUpdate
  void beforeUpdate() {
    updatedAt = Instant.now();
  }
}
