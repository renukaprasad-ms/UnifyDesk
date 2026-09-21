package com.messaging.inbox.service;

import com.messaging.channel.email.EmailReadStateMessage;
import com.messaging.channel.email.EmailProviderMetrics;
import com.messaging.channel.email.EmailProviderOperation;
import com.messaging.channel.email.EmailReadStateProvider;
import com.messaging.channel.entity.ChannelConnection;
import com.messaging.channel.provider.ChannelProviderRegistry;
import com.messaging.inbox.entity.InboxMessage;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class InboxProviderReadStateService {

  private final ChannelProviderRegistry providerRegistry;
  private final EmailProviderMetrics metrics;

  public void update(
      ChannelConnection connection,
      List<InboxMessage> messages,
      boolean read,
      boolean background) {
    if (messages == null || messages.isEmpty()) return;

    EmailReadStateProvider provider =
        providerRegistry.requireCapability(
            connection.getProviderType(), EmailReadStateProvider.class);

    ArrayList<EmailReadStateMessage> targets = new ArrayList<>(messages.size());
    for (InboxMessage message : messages) {
      targets.add(
          new EmailReadStateMessage(
              message.getExternalMessageId(), message.getProviderFolderKey()));
    }

    metrics.record(
        connection.getProviderType(),
        EmailProviderOperation.READ_STATE,
        () -> provider.updateReadState(connection, targets, read, background));
  }
}
