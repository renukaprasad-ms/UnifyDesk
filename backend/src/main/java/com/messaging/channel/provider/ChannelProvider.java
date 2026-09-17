package com.messaging.channel.provider;

import com.messaging.channel.enums.ChannelType;
import com.messaging.channel.enums.ProviderType;

public interface ChannelProvider {

  ProviderType providerType();

  default ChannelType channelType() {
    return providerType().channelType();
  }
}
