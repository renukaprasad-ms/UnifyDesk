package com.messaging.broadcast.transport;

public record BroadcastSmsCommand(String recipient, String bodyText, String correlationId) {}
