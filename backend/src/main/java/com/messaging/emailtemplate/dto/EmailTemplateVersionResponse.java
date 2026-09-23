package com.messaging.emailtemplate.dto;

import com.messaging.emailtemplate.entity.EmailTemplateVersion;
import com.messaging.emailtemplate.enums.EmailTemplateEditorMode;
import java.time.Instant;

public record EmailTemplateVersionResponse(
    String id,
    long version,
    EmailTemplateEditorMode editorMode,
    String subject,
    String contentHash,
    Instant createdAt,
    Instant publishedAt) {
  public static EmailTemplateVersionResponse from(EmailTemplateVersion version) {
    return new EmailTemplateVersionResponse(
        version.getId().toString(),
        version.getVersionNumber(),
        version.getEditorMode(),
        version.getSubjectTemplate(),
        version.getContentHash(),
        version.getCreatedAt(),
        version.getPublishedAt());
  }
}
