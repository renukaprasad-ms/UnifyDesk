package com.messaging.channel.microsoft;

import com.messaging.channel.entity.ChannelConnection;
import com.messaging.channel.enums.ChannelConnectionStatus;
import com.messaging.channel.enums.ProviderType;
import com.messaging.channel.provider.*;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class MicrosoftProvider implements ChannelProvider {

  @Override
  public ProviderType providerType() {
    return ProviderType.MICROSOFT;
  }

  @Override
  public ProviderSendResult send(ProviderSendRequest request) {
    throw new com.messaging.common.exception.BadRequestException(
        "Use the Microsoft email send endpoint");
  }

  @Override
  public List<ProviderWebhookMessage> parseWebhook(ProviderWebhookRequest request) {
    return List.of();
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
