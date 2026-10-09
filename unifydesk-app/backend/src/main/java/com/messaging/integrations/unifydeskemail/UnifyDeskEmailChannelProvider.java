package com.messaging.integrations.unifydeskemail;

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
public class UnifyDeskEmailChannelProvider implements DisconnectableChannelProvider {
  private final UnifyDeskEmailChannelLifecycleService lifecycle;

  @Override
  public ProviderType providerType() { return ProviderType.UNIFYDESK_EMAIL; }

  @Override
  public void disconnect(ChannelConnection connection) {
    lifecycle.disconnect(connection.getCompanyId(), connection.getId());
  }

  @Override
  public ProviderAccountHealth health(ChannelConnection connection) {
    boolean active = connection.getStatus() == ChannelConnectionStatus.ACTIVE;
    return new ProviderAccountHealth(active, connection.getStatus().name(),
        active ? "UnifyDesk Email connection is active" : "Connection inactive", Instant.now());
  }
}
