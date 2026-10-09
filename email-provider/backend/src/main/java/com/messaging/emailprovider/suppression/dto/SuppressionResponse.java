package com.messaging.emailprovider.suppression.dto;

import com.messaging.emailprovider.suppression.EmailSuppression;
import com.messaging.emailprovider.suppression.SuppressionReason;
import java.time.Instant;

public record SuppressionResponse(
    String id, String email, SuppressionReason reason, Instant expiresAt, Instant createdAt) {

  public static SuppressionResponse from(EmailSuppression suppression) {
    return new SuppressionResponse(
        suppression.getId().toString(),
        suppression.getRecipientAddress(),
        suppression.getReason(),
        suppression.getExpiresAt(),
        suppression.getCreatedAt());
  }
}
