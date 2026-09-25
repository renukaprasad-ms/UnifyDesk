package com.messaging.kafka.event;

public record NotificationRequestedEvent(
    Long companyId, String recipient, String notificationType, String templateCode) {}
