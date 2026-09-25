package com.messaging.broadcast.ses;

import com.messaging.broadcast.service.AmazonSesChannelLifecycleService;
import com.messaging.channel.entity.ChannelConnection;
import com.messaging.channel.enums.ChannelConnectionStatus;
import com.messaging.channel.enums.ProviderType;
import com.messaging.channel.provider.DisconnectableChannelProvider;
import com.messaging.channel.provider.ProviderAccountHealth;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AmazonSesChannelProvider implements DisconnectableChannelProvider {

  private final AmazonSesChannelLifecycleService lifecycleService;

  @Override
  public ProviderType providerType() {
    return ProviderType.AMAZON_SES;
  }

  @Override
  public void disconnect(ChannelConnection connection) {
    lifecycleService.disconnect(connection.getCompanyId(), connection.getId());
  }

  @Override
  public ProviderAccountHealth health(ChannelConnection connection) {
    boolean healthy = connection.getStatus() == ChannelConnectionStatus.ACTIVE;
    return new ProviderAccountHealth(
        healthy,
        connection.getStatus().name(),
        healthy ? "Amazon SES broadcast connection is active" : "Amazon SES connection is not active",
        Instant.now());
  }
}
