package com.messaging.emailtemplate.entity;

import com.messaging.common.id.IdGenerator;
import com.messaging.emailtemplate.enums.EmailTemplateEditorMode;
import com.messaging.emailtemplate.enums.EmailTemplatePurpose;
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
    name = "email_template_versions",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_email_template_versions_template_version",
            columnNames = {"template_id", "version_number"}),
    indexes = @Index(name = "idx_email_template_versions_template", columnList = "template_id,version_number"))
public class EmailTemplateVersion {

  @Id private Long id;

  @Column(name = "template_id", nullable = false)
  private Long templateId;

  @Column(name = "version_number", nullable = false)
  private long versionNumber;

  @Enumerated(EnumType.STRING)
  @Column(name = "editor_mode", nullable = false, length = 16)
  private EmailTemplateEditorMode editorMode;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private EmailTemplatePurpose purpose;

  @Column(name = "subject_template", length = 998)
  private String subjectTemplate;

  @Column(name = "preheader_template", length = 255)
  private String preheaderTemplate;

  @Column(name = "document_json", columnDefinition = "text")
  private String documentJson;

  @Column(name = "html_source", columnDefinition = "text")
  private String htmlSource;

  @Column(name = "compiled_html", nullable = false, columnDefinition = "text")
  private String compiledHtml;

  @Column(name = "compiled_text", nullable = false, columnDefinition = "text")
  private String compiledText;

  @Column(name = "variables_json", nullable = false, columnDefinition = "text")
  private String variablesJson;

  @Column(name = "content_hash", nullable = false, length = 64)
  private String contentHash;

  @Column(name = "created_by_user_id", nullable = false)
  private Long createdByUserId;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "published_at")
  private Instant publishedAt;

  @PrePersist
  void beforeInsert() {
    if (id == null) id = IdGenerator.nextId();
    if (purpose == null) purpose = EmailTemplatePurpose.MARKETING;
    if (createdAt == null) createdAt = Instant.now();
  }
}
