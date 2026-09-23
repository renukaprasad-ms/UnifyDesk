package com.messaging.emailtemplate.dto;

import com.messaging.emailtemplate.entity.EmailTemplateCatalogItem;
import com.messaging.emailtemplate.enums.*;
import java.util.List;

public record EmailTemplateCatalogResponse(
    String id,
    String slug,
    String name,
    String description,
    EmailTemplateCategory category,
    EmailTemplatePurpose purpose,
    EmailTemplateEditorMode editorMode,
    String subject,
    String preheader,
    String documentJson,
    String htmlSource,
    String previewHtml,
    List<String> variables) {
  public static EmailTemplateCatalogResponse from(
      EmailTemplateCatalogItem item, String previewHtml, List<String> variables) {
    return new EmailTemplateCatalogResponse(
        item.getId().toString(),
        item.getSlug(),
        item.getName(),
        item.getDescription(),
        item.getCategory(),
        item.getPurpose(),
        item.getEditorMode(),
        item.getSubjectTemplate(),
        item.getPreheaderTemplate(),
        item.getDocumentJson(),
        item.getHtmlSource(),
        previewHtml,
        variables);
  }
}
