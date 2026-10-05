package com.messaging.channel.microsoft;

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
import com.messaging.channel.microsoft.dto.MicrosoftSendRequest;
import com.messaging.channel.microsoft.dto.MicrosoftSendResponse;
import com.messaging.channel.microsoft.dto.MicrosoftSyncQueuedResponse;
import com.messaging.channel.provider.DisconnectableChannelProvider;
import com.messaging.channel.provider.ProviderAccountHealth;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MicrosoftProvider
    implements EmailSendProvider,
        EmailSyncProvider,
        EmailReadStateProvider,
        DisconnectableChannelProvider {

  private final MicrosoftSendService sendService;
  private final MicrosoftBackfillQueueService syncQueueService;
  private final MicrosoftReadStateService readStateService;
  private final MicrosoftSubscriptionService subscriptionService;

  @Override
  public ProviderType providerType() {
    return ProviderType.MICROSOFT;
  }

  @Override
  public EmailSendResult send(ChannelConnection connection, EmailSendCommand command) {
    MicrosoftSendResponse response =
        sendService.send(
            connection,
            new MicrosoftSendRequest(
                command.to(),
                command.cc(),
                command.bcc(),
                command.subject(),
                command.bodyText(),
                command.replyToMessageId(),
                command.replyAll(),
                command.attachmentMediaIds().stream().map(String::valueOf).toList()));

    return new EmailSendResult(
        response.connectionId(),
        null,
        null,
        response.from(),
        response.to(),
        response.cc(),
        response.bcc(),
        response.subject(),
        response.acceptedAt());
  }

  @Override
  public EmailSyncQueuedResult queueSync(
      ChannelConnection connection, Long userId, EmailSyncRequest request) {
    MicrosoftSyncQueuedResponse response =
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
  public void disconnect(ChannelConnection connection) {
    subscriptionService.disconnect(connection);
  }

  @Override
  public ProviderAccountHealth health(ChannelConnection connection) {
    boolean healthy = connection.getStatus() == ChannelConnectionStatus.ACTIVE;
    return new ProviderAccountHealth(
        healthy,
        connection.getStatus().name(),
        healthy ? "Microsoft mailbox is active" : "Microsoft mailbox is not active",
        Instant.now());
  }
}
