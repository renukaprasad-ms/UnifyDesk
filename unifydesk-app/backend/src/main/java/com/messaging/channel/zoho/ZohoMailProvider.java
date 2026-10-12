package com.messaging.channel.zoho;

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
import com.messaging.channel.provider.DisconnectableChannelProvider;
import com.messaging.channel.provider.ProviderAccountHealth;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ZohoMailProvider
    implements EmailSendProvider,
        EmailSyncProvider,
        EmailReadStateProvider,
        DisconnectableChannelProvider {

  private final ZohoMailSendService sendService;
  private final ZohoMailSyncQueueService syncQueueService;
  private final ZohoMailReadStateService readStateService;
  private final ZohoMailDisconnectService disconnectService;

  @Override
  public ProviderType providerType() {
    return ProviderType.ZOHO_MAIL;
  }

  @Override
  public EmailSendResult send(ChannelConnection connection, EmailSendCommand command) {
    return sendService.send(connection, command);
  }

  @Override
  public EmailSyncQueuedResult queueSync(
      ChannelConnection connection, Long userId, EmailSyncRequest request) {
    return syncQueueService.enqueue(
        connection.getCompanyId(), userId, connection.getId(), request);
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
    disconnectService.disconnect(connection);
  }

  @Override
  public ProviderAccountHealth health(ChannelConnection connection) {
    boolean healthy = connection.getStatus() == ChannelConnectionStatus.ACTIVE;
    return new ProviderAccountHealth(
        healthy,
        connection.getStatus().name(),
        healthy ? "Zoho Mail connection is active" : "Zoho Mail connection is not active",
        Instant.now());
  }
}
