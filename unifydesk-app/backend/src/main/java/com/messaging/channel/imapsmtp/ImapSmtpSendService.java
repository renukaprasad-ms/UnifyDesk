package com.messaging.channel.imapsmtp;

import com.messaging.channel.email.EmailAttachment;
import com.messaging.channel.email.EmailAttachmentService;
import com.messaging.channel.email.EmailFolder;
import com.messaging.channel.entity.ChannelConnection;
import com.messaging.channel.imapsmtp.ImapSmtpMailClient.MailboxMessage;
import com.messaging.channel.imapsmtp.ImapSmtpMailClient.SentMessage;
import com.messaging.channel.imapsmtp.dto.ImapSmtpSendRequest;
import com.messaging.channel.imapsmtp.dto.ImapSmtpSendResponse;
import com.messaging.channel.enums.ProviderType;
import com.messaging.inbox.dto.InboxEmailChangedEvent;
import com.messaging.realtime.service.RealtimeService;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ImapSmtpSendService {

  private final ImapSmtpConnectionService connectionService;
  private final EmailAttachmentService attachmentService;
  private final ImapSmtpMailClient mailClient;
  private final ImapSmtpInboxPersistenceService persistenceService;
  private final RealtimeService realtimeService;

  public ImapSmtpSendResponse send(
      ChannelConnection connection, ImapSmtpSendRequest request) {
    Long connectionId = connection.getId();
    ImapSmtpStoredCredentials credentials = connectionService.credentials(connection);

    List<EmailAttachment> attachments =
        attachmentService.resolve(connection.getCompanyId(), request.attachmentIds());
    SentMessage sent = mailClient.send(credentials, request, attachments);
    String messageId =
        sent.messageId() == null || sent.messageId().isBlank()
            ? "<" + UUID.randomUUID() + "@local.messaging>"
            : sent.messageId();
    String threadKey = threadKey(messageId, request.inReplyTo(), request.references());

    MailboxMessage local =
        new MailboxMessage(
            0,
            0,
            "smtp:" + messageId,
            messageId,
            threadKey,
            credentials.email(),
            null,
            sent.to(),
            sent.cc(),
            sent.bcc(),
            trimToNull(request.inReplyTo()),
            trimToNull(request.references()),
            sent.subject(),
            request.bodyText(),
            null,
            sent.sentAt(),
            false,
            EmailFolder.SENT);
    var persisted = persistenceService.persist(connection, List.of(local));
    realtimeService.sendToCompany(
        connection.getCompanyId(),
        "inbox.email.changed",
        InboxEmailChangedEvent.boundedDelta(
            connection.getId(),
            ProviderType.IMAP_SMTP,
            persisted.conversationIds(),
            persisted.messageIds(),
            List.of()));

    return new ImapSmtpSendResponse(
        connectionId.toString(),
        messageId,
        credentials.email(),
        sent.to(),
        sent.cc(),
        sent.bcc(),
        sent.subject(),
        sent.sentAt());
  }

  private String threadKey(String messageId, String inReplyTo, String references) {
    if (references != null && !references.isBlank()) {
      String[] tokens = references.trim().split("\\s+");
      if (tokens.length > 0 && !tokens[0].isBlank()) return tokens[0];
    }
    if (inReplyTo != null && !inReplyTo.isBlank()) return inReplyTo.trim();
    return messageId.trim().toLowerCase(Locale.ROOT);
  }

  private String trimToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
