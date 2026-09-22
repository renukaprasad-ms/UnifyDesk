package com.messaging.template.dto;

import com.messaging.channel.enums.ChannelType;
import com.messaging.template.entity.MessageTemplate;
import com.messaging.template.enums.MessageTemplateCategory;
import com.messaging.template.enums.MessageTemplateProviderStatus;
import com.messaging.template.enums.MessageTemplateStatus;
import java.time.Instant;
import java.util.List;

public record MessageTemplateResponse(
    String id,
    String companyId,
    String name,
    ChannelType channelType,
    MessageTemplateCategory category,
    MessageTemplateStatus status,
    MessageTemplateProviderStatus providerStatus,
    String providerType,
    String externalTemplateId,
    String language,
    String subject,
    String headerText,
    String bodyText,
    String bodyHtml,
    String footerText,
    List<MessageTemplateButton> buttons,
    List<String> variables,
    Instant createdAt,
    Instant updatedAt,
    Instant archivedAt) {

  public static MessageTemplateResponse from(
      MessageTemplate template,
      List<MessageTemplateButton> buttons,
      List<String> variables) {
    return new MessageTemplateResponse(
        template.getId().toString(),
        template.getCompanyId().toString(),
        template.getName(),
        template.getChannelType(),
        template.getCategory(),
        template.getStatus(),
        template.getProviderStatus(),
        template.getProviderType(),
        template.getExternalTemplateId(),
        template.getLanguage(),
        template.getSubject(),
        template.getHeaderText(),
        template.getBodyText(),
        template.getBodyHtml(),
        template.getFooterText(),
        buttons,
        variables,
        template.getCreatedAt(),
        template.getUpdatedAt(),
        template.getArchivedAt());
  }
}
