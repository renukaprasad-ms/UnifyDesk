package com.messaging.managedemail.events;

import java.time.Instant;

public record ManagedEmailProviderEvent(
    String snsMessageId,
    ManagedEmailProviderEventType type,
    Instant eventTimestamp,
    String providerMessageId,
    Long companyId,
    Long runId,
    Long senderId,
    Long domainId,
    Long recipientId,
    String correlation,
    String bounceType,
    String diagnosticCode,
    String payloadHash) {

  public boolean hardBounce() {
    return type == ManagedEmailProviderEventType.BOUNCE
        && "PERMANENT".equalsIgnoreCase(bounceType);
  }
}
