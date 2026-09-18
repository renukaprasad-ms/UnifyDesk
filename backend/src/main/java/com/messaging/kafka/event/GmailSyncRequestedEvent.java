package com.messaging.kafka.event;

public record GmailSyncRequestedEvent(Long connectionId, boolean initialSync) {}
