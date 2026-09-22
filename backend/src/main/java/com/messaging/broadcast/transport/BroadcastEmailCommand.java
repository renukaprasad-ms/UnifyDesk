package com.messaging.broadcast.transport;

import java.util.Map;

public record BroadcastEmailCommand(
    String recipient,
    String subject,
    String bodyText,
    String bodyHtml,
    String providerTemplateId,
    Map<String, String> templateVariables,
    String requestedInternetMessageId,
    String correlationId) {}
