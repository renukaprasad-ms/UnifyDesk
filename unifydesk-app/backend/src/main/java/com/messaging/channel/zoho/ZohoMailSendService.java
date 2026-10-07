package com.messaging.channel.zoho;

import com.messaging.channel.email.EmailAttachment;
import com.messaging.channel.email.EmailAttachmentService;
import com.messaging.channel.email.EmailSendCommand;
import com.messaging.channel.email.EmailSendResult;
import com.messaging.channel.entity.ChannelConnection;
import com.messaging.channel.zoho.ZohoMailApiClient.ReplyRequest;
import com.messaging.channel.zoho.ZohoMailApiClient.SendData;
import com.messaging.channel.zoho.ZohoMailApiClient.SendRequest;
import com.messaging.channel.zoho.ZohoMailApiClient.UploadedAttachment;
import com.messaging.common.exception.BadRequestException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ZohoMailSendService {

  private final ZohoMailTokenService tokenService;
  private final ZohoMailApiClient apiClient;
  private final EmailAttachmentService attachmentService;

  public EmailSendResult send(ChannelConnection connection, EmailSendCommand command) {
    ZohoMailStoredToken token = tokenService.tokenSet(connection);
    List<String> to = normalize(command.to());
    List<String> cc = normalize(command.cc());
    List<String> bcc = normalize(command.bcc());
    if (to.isEmpty() && cc.isEmpty() && bcc.isEmpty()) {
      throw new BadRequestException("At least one email recipient is required");
    }

    List<UploadedAttachment> uploaded = new ArrayList<>();
    for (EmailAttachment attachment :
        attachmentService.resolve(connection.getCompanyId(), command.attachmentMediaIds())) {
      uploaded.add(
          apiClient.uploadAttachment(
              token.mailApiBaseUrl(),
              token.accessToken(),
              token.accountId(),
              attachment.fileName(),
              attachment.openStream()));
    }

    String toAddress = String.join(",", to);
    String ccAddress = String.join(",", cc);
    String bccAddress = String.join(",", bcc);
    String subject = command.subject() == null ? "" : command.subject().trim();
    String body = command.bodyText() == null ? "" : command.bodyText();

    SendData response;
    if (hasText(command.replyToMessageId())) {
      response =
          apiClient.reply(
              token.mailApiBaseUrl(),
              token.accessToken(),
              token.accountId(),
              command.replyToMessageId().trim(),
              new ReplyRequest(
                  token.emailAddress(),
                  toAddress,
                  ccAddress,
                  bccAddress,
                  subject,
                  body,
                  "reply",
                  "plaintext",
                  uploaded));
    } else {
      response =
          apiClient.send(
              token.mailApiBaseUrl(),
              token.accessToken(),
              token.accountId(),
              new SendRequest(
                  token.emailAddress(),
                  toAddress,
                  ccAddress,
                  bccAddress,
                  subject,
                  body,
                  "plaintext",
                  uploaded,
                  command.inReplyTo(),
                  command.references()));
    }

    return new EmailSendResult(
        connection.getId().toString(),
        response.messageId(),
        null,
        token.emailAddress(),
        to,
        cc,
        bcc,
        subject,
        Instant.now());
  }

  private List<String> normalize(List<String> values) {
    if (values == null || values.isEmpty()) return List.of();
    LinkedHashSet<String> result = new LinkedHashSet<>();
    for (String value : values) {
      if (!hasText(value) || !value.contains("@")) {
        throw new BadRequestException("Invalid email recipient");
      }
      result.add(value.trim().toLowerCase(Locale.ROOT));
    }
    return List.copyOf(result);
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}
