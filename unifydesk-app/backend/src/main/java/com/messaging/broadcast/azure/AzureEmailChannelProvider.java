package com.messaging.broadcast.azure;

import com.messaging.broadcast.service.AzureEmailChannelLifecycleService;
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
public class AzureEmailChannelProvider implements DisconnectableChannelProvider {

  private final AzureEmailChannelLifecycleService lifecycleService;

  @Override
  public ProviderType providerType() {
    return ProviderType.AZURE_EMAIL;
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
        healthy ? "Azure Email broadcast connection is active" : "Azure Email connection is not active",
        Instant.now());
  }
}
