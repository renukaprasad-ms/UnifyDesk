package com.messaging.emailtemplate.dto;

import com.messaging.emailtemplate.entity.EmailTemplate;
import com.messaging.emailtemplate.entity.EmailTemplateVersion;
import com.messaging.emailtemplate.enums.*;
import java.time.Instant;
import java.util.List;

public record EmailTemplateResponse(
    String id,
    String name,
    EmailTemplateCategory category,
    EmailTemplateStatus status,
    String currentVersionId,
    long currentVersion,
    String publishedVersionId,
    Long publishedVersion,
    boolean hasDraft,
    EmailTemplateEditorMode editorMode,
    String subject,
    String preheader,
    String documentJson,
    String htmlSource,
    String compiledHtml,
    String compiledText,
    List<String> variables,
    Instant createdAt,
    Instant updatedAt,
    Instant archivedAt) {

  public static EmailTemplateResponse from(
      EmailTemplate template,
      EmailTemplateVersion current,
      EmailTemplateVersion published,
      List<String> variables) {
    return new EmailTemplateResponse(
        template.getId().toString(),
        template.getName(),
        template.getCategory(),
        template.getStatus(),
        current == null ? null : current.getId().toString(),
        current == null ? 0 : current.getVersionNumber(),
        published == null ? null : published.getId().toString(),
        published == null ? null : published.getVersionNumber(),
        published == null || current == null || !published.getId().equals(current.getId()),
        current == null ? EmailTemplateEditorMode.BUILDER : current.getEditorMode(),
        current == null ? null : current.getSubjectTemplate(),
        current == null ? null : current.getPreheaderTemplate(),
        current == null ? null : current.getDocumentJson(),
        current == null ? null : current.getHtmlSource(),
        current == null ? "" : current.getCompiledHtml(),
        current == null ? "" : current.getCompiledText(),
        variables,
        template.getCreatedAt(),
        template.getUpdatedAt(),
        template.getArchivedAt());
  }
}
