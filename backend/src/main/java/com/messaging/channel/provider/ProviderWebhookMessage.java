package com.messaging.channel.provider;

import com.messaging.channel.enums.ChannelType;
import java.time.Instant;
import java.util.Map;

public record ProviderWebhookMessage(
    ChannelType channelType,
    String externalAccountId,
    String externalSenderId,
    String externalMessageId,
    String from,
    String to,
    String contentType,
    String content,
    Instant occurredAt,
    Map<String, Object> metadata) {}
