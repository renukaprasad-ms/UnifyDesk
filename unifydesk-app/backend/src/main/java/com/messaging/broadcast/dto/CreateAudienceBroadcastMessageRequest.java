package com.messaging.broadcast.dto;

import com.messaging.broadcast.enums.BroadcastAudienceSourceType;
import com.messaging.channel.enums.ChannelType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

public record CreateAudienceBroadcastMessageRequest(
    @NotNull BroadcastAudienceSourceType sourceType,
    Long sourceId,
    @Size(max = 500) List<Long> contactIds,
    @NotNull ChannelType channelType,
    Long templateId,
    Long managedSmsTemplateId,
    Long whatsappTemplateId,
    @Valid BroadcastWhatsAppTemplateParameters whatsappTemplateParameters,
    @NotNull Long senderId,
    @Size(max = 998) String subject,
    @Size(max = 500000) String bodyText,
    @Size(max = 900000) String bodyHtml,
    @Size(max = 100) Map<String, String> templateVariables) {

  public CreateAudienceBroadcastMessageRequest(
      BroadcastAudienceSourceType sourceType,
      Long sourceId,
      ChannelType channelType,
      Long templateId,
      Long managedSmsTemplateId,
      Long senderId,
      String subject,
      String bodyText,
      String bodyHtml,
      Map<String, String> templateVariables) {
    this(
        sourceType,
        sourceId,
        null,
        channelType,
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
}
