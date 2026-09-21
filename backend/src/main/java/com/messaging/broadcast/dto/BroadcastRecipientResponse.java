package com.messaging.broadcast.dto;

import com.messaging.broadcast.entity.BroadcastRecipient;
import com.messaging.broadcast.enums.BroadcastRecipientStatus;
import java.time.Instant;

public record BroadcastRecipientResponse(
    String id,
    String destination,
    String displayName,
    BroadcastRecipientStatus status,
    int attemptCount,
    String providerMessageId,
    String internetMessageId,
    Instant acceptedAt,
    Instant bouncedAt,
    Instant failedAt,
    String failureCode,
    String failureReason) {

  public static BroadcastRecipientResponse from(BroadcastRecipient recipient) {
    return new BroadcastRecipientResponse(
        recipient.getId().toString(),
        recipient.getDestination(),
        recipient.getDisplayName(),
        recipient.getStatus(),
        recipient.getAttemptCount(),
        recipient.getProviderMessageId(),
        recipient.getInternetMessageId(),
        recipient.getAcceptedAt(),
        recipient.getBouncedAt(),
        recipient.getFailedAt(),
        recipient.getFailureCode(),
        recipient.getFailureReason());
  }
}
