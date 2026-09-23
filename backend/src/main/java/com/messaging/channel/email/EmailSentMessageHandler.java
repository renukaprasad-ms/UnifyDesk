package com.messaging.channel.email;

import com.messaging.channel.entity.ChannelConnection;
import com.messaging.channel.enums.ProviderType;
import java.time.Instant;

/**
 * Extension point for provider Sent-folder messages that belong to non-inbox workflows.
 *
 * <p>Returning true means the message has been consumed by the handler and should not be persisted
 * into the normal Inbox/Sent conversation model.
 */
public interface EmailSentMessageHandler {

  boolean handle(
      ChannelConnection connection,
      ProviderType providerType,
      String correlationId,
      String providerMessageId,
      String internetMessageId,
      Instant sentAt);
}
