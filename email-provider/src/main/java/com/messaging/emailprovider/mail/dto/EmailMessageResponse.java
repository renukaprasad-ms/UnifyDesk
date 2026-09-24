package com.messaging.emailprovider.mail.dto;

import com.messaging.emailprovider.mail.entity.EmailMessage;
import com.messaging.emailprovider.mail.enums.EmailMessageStatus;
import java.time.Instant;
import java.util.List;

public record EmailMessageResponse(
    String id,
    String companyId,
    String sendingDomainId,
    String from,
    String replyTo,
    String subject,
    String text,
    String html,
    EmailMessageStatus status,
    Instant acceptedAt,
    Instant createdAt,
    Instant updatedAt,
    List<EmailRecipientResponse> recipients) {

  public static EmailMessageResponse from(
      EmailMessage message, List<EmailRecipientResponse> recipients) {
    return new EmailMessageResponse(
        message.getId().toString(),
        message.getCompanyId().toString(),
        message.getSendingDomainId().toString(),
        message.getFromAddress(),
        message.getReplyToAddress(),
        message.getSubject(),
        message.getTextBody(),
        message.getHtmlBody(),
        message.getStatus(),
        message.getAcceptedAt(),
        message.getCreatedAt(),
        message.getUpdatedAt(),
        recipients);
  }
}
