package com.messaging.emailprovider.feedback.dto;

import com.messaging.emailprovider.feedback.EmailEventSource;
import com.messaging.emailprovider.feedback.EmailEventType;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record PostfixFeedbackRequest(
    Long recipientId,
    String queueId,
    @NotNull EmailEventType eventType,
    EmailEventSource source,
    String externalEventId,
    String smtpStatusCode,
    String diagnostic,
    Instant occurredAt) {

  public EmailEventSource resolvedSource() {
    return source == null ? EmailEventSource.POSTFIX_LOG : source;
  }

  public Instant resolvedOccurredAt() {
    return occurredAt == null ? Instant.now() : occurredAt;
  }
}
