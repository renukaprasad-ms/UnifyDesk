package com.messaging.emailprovider.mail.dto;

import com.messaging.emailprovider.mail.entity.EmailRecipient;
import com.messaging.emailprovider.mail.enums.EmailRecipientStatus;
import com.messaging.emailprovider.mail.enums.EmailRecipientType;
import java.time.Instant;

public record EmailRecipientResponse(
    String id,
    String email,
    EmailRecipientType type,
    EmailRecipientStatus status,
    int attemptCount,
    String smtpStatusCode,
    String smtpResponse,
    Instant nextAttemptAt,
    Instant handedOffAt,
    Instant deliveredAt) {

  public static EmailRecipientResponse from(EmailRecipient recipient) {
    return new EmailRecipientResponse(
        recipient.getId().toString(),
        recipient.getRecipientAddress(),
        recipient.getRecipientType(),
        recipient.getStatus(),
        recipient.getAttemptCount(),
        recipient.getSmtpStatusCode(),
        recipient.getSmtpResponse(),
        recipient.getNextAttemptAt(),
        recipient.getHandedOffAt(),
        recipient.getDeliveredAt());
  }
}
