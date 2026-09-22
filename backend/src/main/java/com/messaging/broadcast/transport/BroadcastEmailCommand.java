package com.messaging.broadcast.transport;

public record BroadcastEmailCommand(
    String recipient,
    String subject,
    String bodyText,
    String bodyHtml,
    String requestedInternetMessageId,
    String correlationId) {}
