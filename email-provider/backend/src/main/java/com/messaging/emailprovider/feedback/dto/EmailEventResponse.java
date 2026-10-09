package com.messaging.emailprovider.feedback.dto;

import com.messaging.emailprovider.feedback.EmailEvent;
import com.messaging.emailprovider.feedback.EmailEventSource;
import com.messaging.emailprovider.feedback.EmailEventType;
import java.time.Instant;

public record EmailEventResponse(
    String id,
    EmailEventType eventType,
    EmailEventSource source,
    String smtpStatusCode,
    String diagnostic,
    Instant occurredAt) {

  public static EmailEventResponse from(EmailEvent event) {
    return new EmailEventResponse(
        event.getId().toString(),
        event.getEventType(),
        event.getSource(),
        event.getSmtpStatusCode(),
        event.getDiagnostic(),
        event.getOccurredAt());
  }
}
