package com.messaging.channel.zoho.dto;

import com.messaging.channel.email.EmailSendResult;
import java.time.Instant;
import java.util.List;

public record ZohoMailSendResponse(
    String connectionId,
    String messageId,
    String threadId,
    String from,
    List<String> to,
    List<String> cc,
    List<String> bcc,
    String subject,
    Instant sentAt) {

  public static ZohoMailSendResponse from(EmailSendResult result) {
    return new ZohoMailSendResponse(
        result.connectionId().toString(),
        result.messageId(),
        result.threadId(),
        result.from(),
        result.to(),
        result.cc(),
        result.bcc(),
        result.subject(),
        result.sentAt());
  }
}
