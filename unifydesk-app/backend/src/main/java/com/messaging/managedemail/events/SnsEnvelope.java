package com.messaging.managedemail.events;

import java.time.Instant;

public record SnsEnvelope(
    String type,
    String messageId,
    String topicArn,
    Instant timestamp,
    String message,
    String subscribeUrl) {}
