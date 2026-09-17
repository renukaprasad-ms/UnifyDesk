package com.messaging.channel.provider;

import java.util.Map;

public record ProviderSendRequest(
    Long connectionId,
    Long senderIdentityId,
    String recipient,
    String contentType,
    String content,
    Map<String, Object> metadata) {}
