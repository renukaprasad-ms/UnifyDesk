package com.messaging.broadcast.sendgrid;

import com.messaging.broadcast.service.SendGridChannelLifecycleService;
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
public class SendGridChannelProvider implements DisconnectableChannelProvider {

  private final SendGridChannelLifecycleService lifecycleService;

  @Override
  public ProviderType providerType() {
    return ProviderType.SENDGRID;
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
        healthy ? "SendGrid broadcast connection is active" : "SendGrid connection is not active",
        Instant.now());
  }
}
