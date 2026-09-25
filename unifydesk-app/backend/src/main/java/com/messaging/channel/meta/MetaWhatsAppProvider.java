package com.messaging.channel.meta;

import com.messaging.channel.entity.ChannelConnection;
import com.messaging.channel.enums.ChannelConnectionStatus;
import com.messaging.channel.enums.ProviderType;
import com.messaging.channel.provider.ChannelProvider;
import com.messaging.channel.provider.ProviderAccountHealth;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class MetaWhatsAppProvider implements ChannelProvider {

  @Override
  public ProviderType providerType() {
    return ProviderType.META_WHATSAPP;
  }

  @Override
  public ProviderAccountHealth health(ChannelConnection connection) {
    boolean healthy = connection.getStatus() == ChannelConnectionStatus.ACTIVE;
    return new ProviderAccountHealth(
        healthy,
        connection.getStatus().name(),
        healthy ? "Meta WhatsApp connection is active" : "Meta WhatsApp connection is not active",
        Instant.now());
  }
}
