package com.messaging.broadcast.transport;

import com.messaging.channel.entity.ChannelConnection;
import com.messaging.channel.enums.ProviderType;

public interface BroadcastEmailTransport {
  ProviderType providerType();

  BroadcastEmailResult send(ChannelConnection connection, BroadcastEmailCommand command);
}
