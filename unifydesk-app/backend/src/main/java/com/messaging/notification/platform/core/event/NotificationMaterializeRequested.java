package com.messaging.notification.platform.core.event;

public record NotificationMaterializeRequested(
    String notificationEventId, String eventKey, String eventType, int schemaVersion) {}
