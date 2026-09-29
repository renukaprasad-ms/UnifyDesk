package com.messaging.channel.meta.runtime.dto;

import java.time.Instant;

public record MetaWhatsAppSendResponse(
    String messageId,
    String clientRequestId,
    String providerMessageId,
    String status,
    Instant acceptedAt,
    String safeErrorCode) {}
