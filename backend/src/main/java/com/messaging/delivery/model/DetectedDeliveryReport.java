package com.messaging.delivery.model;

import com.messaging.delivery.enums.EmailBounceType;

public record DetectedDeliveryReport(
    String failedRecipient,
    String normalizedFailedRecipient,
    String originalMessageId,
    String providerCorrelationId,
    String statusCode,
    String diagnosticMessage,
    EmailBounceType bounceType) {}
