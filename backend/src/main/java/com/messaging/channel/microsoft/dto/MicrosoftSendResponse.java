package com.messaging.channel.microsoft.dto;

import com.messaging.channel.email.EmailSendResult;
import java.time.Instant;
import java.util.List;

public record MicrosoftSendResponse(
    String connectionId,
    String from,
    List<String> to,
    List<String> cc,
    List<String> bcc,
    String subject,
    Instant acceptedAt) {

  public static MicrosoftSendResponse from(EmailSendResult result) {
    return new MicrosoftSendResponse(
        result.connectionId(),
        result.from(),
        result.to(),
        result.cc(),
        result.bcc(),
        result.subject(),
        result.acceptedAt());
  }
}
