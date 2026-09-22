package com.messaging.broadcast.msg91;

import com.messaging.broadcast.service.Msg91ChannelLifecycleService;
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
public class Msg91ChannelProvider implements DisconnectableChannelProvider {

  private final Msg91ChannelLifecycleService lifecycleService;

  @Override
  public ProviderType providerType() {
    return ProviderType.MSG91_SMS;
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
        healthy ? "MSG91 SMS broadcast connection is active" : "MSG91 connection is not active",
        Instant.now());
  }
}
