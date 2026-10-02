package com.messaging.broadcast.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Map;

public record UpdateBroadcastMessageRequest(
    Long templateId,
    Long managedSmsTemplateId,
    Long whatsappTemplateId,
    @Valid BroadcastWhatsAppTemplateParameters whatsappTemplateParameters,
    @NotNull Long senderId,
    @Size(max = 998) String subject,
    @Size(max = 500000) String bodyText,
    @Size(max = 900000) String bodyHtml,
    @Size(max = 100) Map<String, String> templateVariables) {

  public UpdateBroadcastMessageRequest(
      Long templateId,
      Long managedSmsTemplateId,
      Long senderId,
      String subject,
      String bodyText,
      String bodyHtml,
      Map<String, String> templateVariables) {
    this(
        templateId,
        managedSmsTemplateId,
        null,
        null,
        senderId,
        subject,
        bodyText,
        bodyHtml,
        templateVariables);
  }

  public UpdateBroadcastMessageRequest(
      Long templateId,
      Long senderId,
      String subject,
      String bodyText,
      String bodyHtml,
      Map<String, String> templateVariables) {
    this(templateId, null, null, null, senderId, subject, bodyText, bodyHtml, templateVariables);
  }
}
