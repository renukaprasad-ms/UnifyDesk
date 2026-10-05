package com.messaging.channel.gmail.dto;

import com.messaging.channel.email.EmailSendCommand;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record GmailSendRequest(
    @Size(max = 100) List<@Email @Size(max = 320) String> to,
    @Size(max = 100) List<@Email @Size(max = 320) String> cc,
    @Size(max = 100) List<@Email @Size(max = 320) String> bcc,
    @Size(max = 998) String subject,
    @NotBlank @Size(max = 100000) String bodyText,
    @Size(max = 255) String threadId,
    @Size(max = 998) String inReplyTo,
    @Size(max = 10000) String references,
    @Size(max = 10) List<@Size(max = 32) String> attachmentMediaIds) {

  public EmailSendCommand toCommand() {
    return new EmailSendCommand(
        to, cc, bcc, subject, bodyText, threadId, null, false, inReplyTo, references, attachmentIds());
  }
  private List<Long> attachmentIds() {
    if (attachmentMediaIds == null || attachmentMediaIds.isEmpty()) return List.of();
    try {
      return attachmentMediaIds.stream().map(Long::valueOf).toList();
    } catch (NumberFormatException exception) {
      throw new IllegalArgumentException("Invalid attachment media id", exception);
    }
  }


}
