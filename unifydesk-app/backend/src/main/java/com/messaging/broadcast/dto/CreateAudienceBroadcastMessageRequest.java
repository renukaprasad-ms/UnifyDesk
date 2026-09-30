package com.messaging.broadcast.dto;

import com.messaging.broadcast.enums.BroadcastAudienceSourceType;
import com.messaging.channel.enums.ChannelType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Map;

public record CreateAudienceBroadcastMessageRequest(
    @NotNull BroadcastAudienceSourceType sourceType,
    @NotNull Long sourceId,
    @NotNull ChannelType channelType,
    Long templateId,
    Long managedSmsTemplateId,
    @NotNull Long senderId,
    @Size(max = 998) String subject,
    @Size(max = 500000) String bodyText,
    @Size(max = 900000) String bodyHtml,
    @Size(max = 100) Map<String, String> templateVariables) {}
