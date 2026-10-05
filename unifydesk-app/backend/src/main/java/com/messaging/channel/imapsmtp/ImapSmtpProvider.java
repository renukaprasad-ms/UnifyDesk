package com.messaging.channel.imapsmtp;

import com.messaging.asyncjob.dto.AsyncJobResponse;
import com.messaging.channel.email.EmailReadStateMessage;
import com.messaging.channel.email.EmailReadStateProvider;
import com.messaging.channel.email.EmailSendCommand;
import com.messaging.channel.email.EmailSendProvider;
import com.messaging.channel.email.EmailSendResult;
import com.messaging.channel.email.EmailSyncProvider;
import com.messaging.channel.email.EmailSyncQueuedResult;
import com.messaging.channel.email.EmailSyncRequest;
import com.messaging.channel.entity.ChannelConnection;
import com.messaging.channel.enums.ChannelConnectionStatus;
import com.messaging.channel.enums.ProviderType;
import com.messaging.channel.imapsmtp.dto.ImapSmtpSendRequest;
import com.messaging.channel.imapsmtp.dto.ImapSmtpSendResponse;
import com.messaging.channel.imapsmtp.dto.ImapSmtpSyncQueuedResponse;
import com.messaging.channel.provider.ProviderAccountHealth;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ImapSmtpProvider
    implements EmailSendProvider, EmailSyncProvider, EmailReadStateProvider {

  private final ImapSmtpSendService sendService;
  private final ImapSmtpSyncQueueService syncQueueService;
  private final ImapSmtpReadStateService readStateService;

  @Override
  public ProviderType providerType() {
    return ProviderType.IMAP_SMTP;
  }

  @Override
  public EmailSendResult send(ChannelConnection connection, EmailSendCommand command) {
    ImapSmtpSendResponse response =
        sendService.send(
            connection,
            new ImapSmtpSendRequest(
                command.to(),
                command.cc(),
                command.bcc(),
                command.subject(),
                command.bodyText(),
                command.inReplyTo(),
                command.references(),
                command.attachmentMediaIds().stream().map(String::valueOf).toList()));

    return new EmailSendResult(
        response.connectionId(),
        response.messageId(),
        null,
        response.from(),
        response.to(),
        response.cc(),
        response.bcc(),
        response.subject(),
        response.sentAt());
  }

  @Override
  public EmailSyncQueuedResult queueSync(
      ChannelConnection connection, Long userId, EmailSyncRequest request) {
    ImapSmtpSyncQueuedResponse response =
        syncQueueService.enqueue(connection.getCompanyId(), userId, connection.getId(), request);
    return new EmailSyncQueuedResult(
        response.connectionId(),
        response.jobId(),
        response.status(),
        response.mode(),
        response.folder(),
        response.fromDate(),
        response.toDate(),
        response.messageCount());
  }

  @Override
  public AsyncJobResponse activeSync(ChannelConnection connection) {
    return syncQueueService.active(connection.getCompanyId(), connection.getId());
  }

  @Override
  public void updateReadState(
      ChannelConnection connection,
      List<EmailReadStateMessage> messages,
      boolean read,
      boolean background) {
    readStateService.update(connection, messages, read);
  }

  @Override
  public ProviderAccountHealth health(ChannelConnection connection) {
    boolean healthy = connection.getStatus() == ChannelConnectionStatus.ACTIVE;
    return new ProviderAccountHealth(
        healthy,
        connection.getStatus().name(),
        healthy ? "IMAP/SMTP mailbox is active" : "IMAP/SMTP mailbox is not active",
        Instant.now());
  }
}
