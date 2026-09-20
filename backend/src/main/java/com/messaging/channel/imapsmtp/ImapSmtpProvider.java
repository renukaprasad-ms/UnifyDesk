package com.messaging.channel.imapsmtp;

import com.messaging.channel.entity.ChannelConnection;
import com.messaging.channel.enums.ChannelConnectionStatus;
import com.messaging.channel.enums.ProviderType;
import com.messaging.channel.provider.ChannelProvider;
import com.messaging.channel.provider.ProviderAccountHealth;
import com.messaging.channel.provider.ProviderSendRequest;
import com.messaging.channel.provider.ProviderSendResult;
import com.messaging.channel.provider.ProviderWebhookMessage;
import com.messaging.channel.provider.ProviderWebhookRequest;
import com.messaging.common.exception.BadRequestException;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ImapSmtpProvider implements ChannelProvider {

  @Override
  public ProviderType providerType() {
    return ProviderType.IMAP_SMTP;
  }

  @Override
  public ProviderSendResult send(ProviderSendRequest request) {
    throw new BadRequestException("Use the IMAP/SMTP email send endpoint");
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
        healthy ? "IMAP/SMTP mailbox is active" : "IMAP/SMTP mailbox is not active",
        Instant.now());
  }
}
