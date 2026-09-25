package com.messaging.emailprovider.mail.dto;

import com.messaging.emailprovider.mail.entity.EmailMessage;
import com.messaging.emailprovider.mail.enums.EmailMessageStatus;

public record EmailAcceptedResponse(
    String messageId,
    EmailMessageStatus status,
    int queuedRecipients,
    int suppressedRecipients,
    boolean idempotentReplay) {

  public static EmailAcceptedResponse of(
      EmailMessage message, int queued, int suppressed, boolean replay) {
    return new EmailAcceptedResponse(
        message.getId().toString(), message.getStatus(), queued, suppressed, replay);
  }
}
