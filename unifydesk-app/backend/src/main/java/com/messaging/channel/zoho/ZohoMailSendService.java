package com.messaging.channel.zoho;

import com.messaging.channel.email.EmailAttachment;
import com.messaging.channel.email.EmailAttachmentService;
import com.messaging.channel.email.EmailSendCommand;
import com.messaging.channel.email.EmailSendResult;
import com.messaging.channel.entity.ChannelConnection;
import com.messaging.channel.zoho.ZohoMailApiClient.ZohoAttachmentReference;
import com.messaging.channel.zoho.ZohoMailApiClient.ZohoReplyPayload;
import com.messaging.channel.zoho.ZohoMailApiClient.ZohoSendPayload;
import com.messaging.channel.zoho.ZohoMailApiClient.ZohoSentMessage;
import com.messaging.common.exception.BadRequestException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ZohoMailSendService {

  private final ZohoMailTokenService tokenService;
  private final ZohoMailApiClient apiClient;
  private final EmailAttachmentService attachmentService;
  private final ZohoMailSyncStateService syncStateService;

  public EmailSendResult send(ChannelConnection connection, EmailSendCommand command) {
    List<String> to = normalizeRecipients(command.to());
    List<String> cc = normalizeRecipients(command.cc());
    List<String> bcc = normalizeRecipients(command.bcc());
    if (to.isEmpty() && cc.isEmpty() && bcc.isEmpty()) {
      throw new BadRequestException("At least one email recipient is required");
    }

    ZohoMailStoredToken session = tokenService.session(connection);
    List<EmailAttachment> attachments =
        attachmentService.resolve(connection.getCompanyId(), command.attachmentMediaIds());
    List<ZohoAttachmentReference> uploaded = upload(session, attachments);

    String subject = command.subject() == null ? "" : command.subject().trim();
    String body = command.bodyText() == null ? "" : command.bodyText();

    ZohoSentMessage sent;
    String replyToMessageId = trimToNull(command.replyToMessageId());
    if (replyToMessageId != null) {
      sent =
          apiClient.reply(
              session.accessToken(),
              session.mailBaseUrl(),
              session.accountId(),
              replyToMessageId,
              new ZohoReplyPayload(
                  session.emailAddress(),
                  csv(to),
                  csv(cc),
                  csv(bcc),
                  subject,
                  body,
                  "plaintext",
                  "reply",
                  uploaded.isEmpty() ? null : uploaded));
    } else {
      sent =
          apiClient.send(
              session.accessToken(),
              session.mailBaseUrl(),
              session.accountId(),
              new ZohoSendPayload(
                  session.emailAddress(),
                  csv(to),
                  csv(cc),
                  csv(bcc),
                  subject,
                  body,
                  "plaintext",
                  uploaded.isEmpty() ? null : uploaded));
    }

    syncStateService.trigger(connection.getId());

    return new EmailSendResult(
        connection.getId().toString(),
        sent == null ? null : sent.messageId(),
        null,
        session.emailAddress(),
        to,
        cc,
        bcc,
        subject,
        Instant.now());
  }

  private List<ZohoAttachmentReference> upload(
      ZohoMailStoredToken session, List<EmailAttachment> attachments) {
    if (attachments == null || attachments.isEmpty()) return List.of();

    List<ZohoAttachmentReference> uploaded = new ArrayList<>(attachments.size());
    for (EmailAttachment attachment : attachments) {
      var stored =
          apiClient.uploadAttachment(
              session.accessToken(),
              session.mailBaseUrl(),
              session.accountId(),
              attachment);
      uploaded.add(
          new ZohoAttachmentReference(
              stored.storeName(),
              stored.attachmentName(),
              stored.attachmentPath()));
    }
    return List.copyOf(uploaded);
  }

  private List<String> normalizeRecipients(List<String> values) {
    if (values == null || values.isEmpty()) return List.of();
    Set<String> unique = new LinkedHashSet<>();
    for (String value : values) {
      String normalized = trimToNull(value);
      if (normalized != null) unique.add(normalized.toLowerCase(Locale.ROOT));
    }
    return List.copyOf(unique);
  }

  private String csv(List<String> values) {
    return values == null || values.isEmpty() ? null : String.join(",", values);
  }

  private String trimToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
