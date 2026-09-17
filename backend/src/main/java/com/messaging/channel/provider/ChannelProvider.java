package com.messaging.channel.provider;

import com.messaging.channel.entity.ChannelConnection;
import com.messaging.channel.enums.ChannelType;
import com.messaging.channel.enums.ProviderType;
import java.util.List;

public interface ChannelProvider {

  ProviderType providerType();

  default ChannelType channelType() {
    return providerType().channelType();
  }

  ProviderSendResult send(ProviderSendRequest request);

  List<ProviderWebhookMessage> parseWebhook(ProviderWebhookRequest request);

  ProviderAccountHealth health(ChannelConnection connection);
}
