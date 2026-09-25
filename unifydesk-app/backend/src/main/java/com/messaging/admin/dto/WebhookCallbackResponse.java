package com.messaging.admin.dto;

import java.time.Instant;

public record WebhookCallbackResponse(
    String id,
    String providerType,
    String callbackType,
    String endpoint,
    String httpMethod,
    String queryString,
    String remoteAddress,
    String contentType,
    String eventId,
    String requestHeaders,
    String payload,
    String decodedPayload,
    String processingResult,
    String status,
    Integer httpStatus,
    String errorMessage,
    Instant receivedAt,
    Instant completedAt,
    Long durationMs) {}
