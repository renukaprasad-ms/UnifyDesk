package com.messaging.kafka.event;

public record CleanupRequestedEvent(String cleanupType, String scope) {}
