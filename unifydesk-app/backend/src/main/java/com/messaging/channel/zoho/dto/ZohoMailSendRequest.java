package com.messaging.channel.zoho.dto;

import com.messaging.channel.email.EmailSendCommand;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record ZohoMailSendRequest(
    @NotEmpty List<String> to,
    List<String> cc,
    List<String> bcc,
    String subject,
    @NotNull String bodyText,
    String inReplyTo,
    String references,
    List<String> attachmentMediaIds) {

  public EmailSendCommand toCommand() {
    return new EmailSendCommand(
        to,
        cc == null ? List.of() : cc,
        bcc == null ? List.of() : bcc,
        subject,
        bodyText,
        null,
        inReplyTo,
        references,
        null,
        false,
        attachmentMediaIds == null ? List.of() : attachmentMediaIds);
  }
}
