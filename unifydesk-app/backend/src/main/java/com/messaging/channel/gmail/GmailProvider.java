package com.messaging.channel.gmail;

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
import com.messaging.channel.gmail.dto.GmailSendRequest;
import com.messaging.channel.gmail.dto.GmailSendResponse;
import com.messaging.channel.gmail.dto.GmailSyncQueuedResponse;
import com.messaging.channel.provider.DisconnectableChannelProvider;
import com.messaging.channel.provider.ProviderAccountHealth;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GmailProvider
    implements EmailSendProvider,
        EmailSyncProvider,
        EmailReadStateProvider,
        DisconnectableChannelProvider {

  private final GmailSendService sendService;
  private final GmailSyncQueueService syncQueueService;
  private final GmailReadStateService readStateService;
  private final GmailDisconnectService disconnectService;

  @Override
  public ProviderType providerType() {
    return ProviderType.GMAIL;
  }

  @Override
  public EmailSendResult send(ChannelConnection connection, EmailSendCommand command) {
    GmailSendResponse response =
        sendService.send(
            connection,
            new GmailSendRequest(
                command.to(),
                command.cc(),
                command.bcc(),
                command.subject(),
                command.bodyText(),
                command.threadId(),
                command.inReplyTo(),
                command.references(),
                command.attachmentMediaIds().stream().map(String::valueOf).toList()));

    return new EmailSendResult(
        response.connectionId(),
        response.messageId(),
        response.threadId(),
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
    GmailSyncQueuedResponse response =
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
    readStateService.update(connection, messages, read, background);
  }

  @Override
  public void disconnect(ChannelConnection connection) {
    disconnectService.disconnect(connection);
  }

  @Override
  public ProviderAccountHealth health(ChannelConnection connection) {
    boolean healthy = connection.getStatus() == ChannelConnectionStatus.ACTIVE;
    return new ProviderAccountHealth(
        healthy,
        connection.getStatus().name(),
        healthy ? "Gmail connection is active" : "Gmail connection is not active",
        Instant.now());
  }
}
