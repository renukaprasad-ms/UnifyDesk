package com.messaging.emailtemplate.dto;

import com.messaging.emailtemplate.entity.EmailTemplate;
import com.messaging.emailtemplate.entity.EmailTemplateVersion;
import com.messaging.emailtemplate.enums.*;
import java.time.Instant;
import java.util.List;

public record EmailTemplateSummaryResponse(
    String id,
    String name,
    EmailTemplateCategory category,
    EmailTemplatePurpose purpose,
    EmailTemplatePurpose publishedPurpose,
    EmailTemplateStatus status,
    long currentVersion,
    Long publishedVersion,
    boolean hasDraft,
    EmailTemplateEditorMode editorMode,
    String subject,
    List<String> variables,
    Instant createdAt,
    Instant updatedAt) {

  public static EmailTemplateSummaryResponse from(
      EmailTemplate template,
      EmailTemplateVersion current,
      EmailTemplateVersion published,
      List<String> variables) {
    return new EmailTemplateSummaryResponse(
        template.getId().toString(),
        template.getName(),
        template.getCategory(),
        current == null ? template.getPurpose() : current.getPurpose(),
        published == null ? null : published.getPurpose(),
        template.getStatus(),
        current == null ? 0 : current.getVersionNumber(),
        published == null ? null : published.getVersionNumber(),
        published == null || current == null || !published.getId().equals(current.getId()),
        current == null ? EmailTemplateEditorMode.BUILDER : current.getEditorMode(),
        current == null ? null : current.getSubjectTemplate(),
        variables,
        template.getCreatedAt(),
        template.getUpdatedAt());
  }
}
