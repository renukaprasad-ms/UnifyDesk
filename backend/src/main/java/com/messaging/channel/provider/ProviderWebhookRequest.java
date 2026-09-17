package com.messaging.channel.provider;

import java.util.Map;

public record ProviderWebhookRequest(
    String body, Map<String, String> headers, Map<String, String> queryParameters) {}
