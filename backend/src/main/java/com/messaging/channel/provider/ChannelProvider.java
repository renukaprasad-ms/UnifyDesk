package com.messaging.channel.provider;

import com.messaging.channel.entity.ChannelConnection;
import com.messaging.channel.enums.ChannelType;
import com.messaging.channel.enums.ProviderType;

/**
 * Base contract for a channel provider.
 *
 * <p>Provider-specific operations belong in small capability interfaces rather than this base
 * contract. This keeps providers from implementing unsupported operations just to satisfy an
 * oversized interface.
 */
public interface ChannelProvider {

  ProviderType providerType();

  default ChannelType channelType() {
    return providerType().channelType();
  }

  ProviderAccountHealth health(ChannelConnection connection);
}
