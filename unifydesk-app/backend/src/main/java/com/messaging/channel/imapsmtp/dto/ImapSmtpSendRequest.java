package com.messaging.channel.imapsmtp.dto;

import com.messaging.common.exception.BadRequestException;
import com.messaging.channel.email.EmailSendCommand;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record ImapSmtpSendRequest(
    @Size(max = 100) List<@Email @Size(max = 320) String> to,
    @Size(max = 100) List<@Email @Size(max = 320) String> cc,
    @Size(max = 100) List<@Email @Size(max = 320) String> bcc,
    @Size(max = 998) String subject,
    @NotBlank @Size(max = 100000) String bodyText,
    @Size(max = 998) String inReplyTo,
    @Size(max = 10000) String references,
    @Size(max = 10) List<@Size(max = 32) String> attachmentMediaIds) {

  public ImapSmtpSendRequest(
      List<String> to,
      List<String> cc,
      List<String> bcc,
      String subject,
      String bodyText,
      String inReplyTo,
      String references) {
    this(to, cc, bcc, subject, bodyText, inReplyTo, references, List.of());
  }

  public EmailSendCommand toCommand() {
    return new EmailSendCommand(
        to, cc, bcc, subject, bodyText, null, null, false, inReplyTo, references, attachmentIds());
  }
  public List<Long> attachmentIds() {
    if (attachmentMediaIds == null || attachmentMediaIds.isEmpty()) return List.of();
    try {
      return attachmentMediaIds.stream().map(Long::valueOf).toList();
    } catch (NumberFormatException exception) {
      throw new BadRequestException("Invalid attachment media id");
    }
  }


}
