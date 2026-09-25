package com.messaging.emailprovider.feedback;

import com.messaging.emailprovider.mail.entity.EmailMessage;
import com.messaging.emailprovider.mail.entity.EmailRecipient;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class LifecycleEventEncoder {

  public String encode(
      EmailMessage message,
      EmailRecipient recipient,
      EmailEventType eventType,
      Instant occurredAt,
      String smtpStatus,
      String diagnostic) {
    return "{"
        + "\"eventType\":\""
        + eventType
        + "\","
        + "\"companyId\":\""
        + message.getCompanyId()
        + "\","
        + "\"messageId\":\""
        + message.getId()
        + "\","
        + "\"recipientId\":\""
        + recipient.getId()
        + "\","
        + "\"recipient\":\""
        + escape(recipient.getRecipientAddress())
        + "\","
        + "\"sendingDomainId\":\""
        + message.getSendingDomainId()
        + "\","
        + "\"smtpStatusCode\":"
        + nullable(smtpStatus)
        + ","
        + "\"diagnostic\":"
        + nullable(diagnostic)
        + ","
        + "\"occurredAt\":\""
        + occurredAt
        + "\""
        + "}";
  }

  private String nullable(String value) {
    return value == null ? "null" : "\"" + escape(value) + "\"";
  }

  private String escape(String value) {
    return value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\r", "\\r")
        .replace("\n", "\\n")
        .replace("\t", "\\t");
  }
}
