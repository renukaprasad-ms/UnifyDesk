package com.messaging.channel.provider;

import com.messaging.channel.entity.ChannelConnection;

/**
 * Provider capability for external cleanup that must happen before a local disconnect completes.
 */
public interface DisconnectableChannelProvider extends ChannelProvider {

  void disconnect(ChannelConnection connection);
}
