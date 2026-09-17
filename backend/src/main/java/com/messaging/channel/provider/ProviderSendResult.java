package com.messaging.channel.provider;

import java.time.Instant;
import java.util.Map;

public record ProviderSendResult(
    boolean accepted,
    String externalMessageId,
    String providerStatus,
    Instant acceptedAt,
    Map<String, Object> metadata) {}
