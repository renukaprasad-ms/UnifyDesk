package com.messaging.delivery.model;

import com.messaging.broadcast.enums.BroadcastBounceType;

public record DetectedDeliveryReport(
    String failedRecipient,
    String normalizedFailedRecipient,
    String originalMessageId,
    String providerCorrelationId,
    String statusCode,
    String diagnosticMessage,
    BroadcastBounceType bounceType) {}
