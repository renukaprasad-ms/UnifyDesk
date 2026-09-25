package com.messaging.channel.imapsmtp.dto;

import com.messaging.channel.email.EmailSendResult;
import java.time.Instant;
import java.util.List;

public record ImapSmtpSendResponse(
    String connectionId,
    String messageId,
    String from,
    List<String> to,
    List<String> cc,
    List<String> bcc,
    String subject,
    Instant sentAt) {

  public static ImapSmtpSendResponse from(EmailSendResult result) {
    return new ImapSmtpSendResponse(
        result.connectionId(),
        result.externalMessageId(),
        result.from(),
        result.to(),
        result.cc(),
        result.bcc(),
        result.subject(),
        result.acceptedAt());
  }
}
