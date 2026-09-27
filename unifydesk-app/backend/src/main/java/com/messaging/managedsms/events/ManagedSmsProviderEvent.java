package com.messaging.managedsms.events;

import java.math.BigDecimal;
import java.time.Instant;

public record ManagedSmsProviderEvent(
    Long id,
    String providerAccountKey,
    String eventKey,
    ManagedSmsProviderEventType type,
    String correlationToken,
    String providerRequestId,
    String failureReason,
    BigDecimal credit,
    Instant eventAt,
    String payloadJson) {}
