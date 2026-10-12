package com.messaging.channel.enums;

import java.util.EnumSet;
import java.util.Set;

public enum ProviderType {
  GMAIL(ChannelType.EMAIL, ChannelConnectionCapability.INBOX),
  MICROSOFT(ChannelType.EMAIL, ChannelConnectionCapability.INBOX),
  ZOHO_MAIL(ChannelType.EMAIL, ChannelConnectionCapability.INBOX),
  IMAP_SMTP(ChannelType.EMAIL, ChannelConnectionCapability.INBOX),
  SMTP(ChannelType.EMAIL, ChannelConnectionCapability.INBOX),
  AMAZON_SES(ChannelType.EMAIL, ChannelConnectionCapability.BROADCAST),
  UNIFYDESK_EMAIL(ChannelType.EMAIL, ChannelConnectionCapability.BROADCAST),
  AZURE_EMAIL(ChannelType.EMAIL, ChannelConnectionCapability.BROADCAST),
  SENDGRID(ChannelType.EMAIL, ChannelConnectionCapability.BROADCAST),
  META_WHATSAPP(
      ChannelType.WHATSAPP,
      ChannelConnectionCapability.INBOX,
      ChannelConnectionCapability.BROADCAST),
  MSG91_EMAIL(ChannelType.EMAIL, ChannelConnectionCapability.BROADCAST),
  MSG91_SMS(ChannelType.SMS, ChannelConnectionCapability.BROADCAST);

  private final ChannelType channelType;
  private final Set<ChannelConnectionCapability> capabilities;

  ProviderType(ChannelType channelType, ChannelConnectionCapability... capabilities) {
    this.channelType = channelType;
    this.capabilities =
        capabilities.length == 0
            ? Set.of()
            : Set.copyOf(EnumSet.copyOf(java.util.List.of(capabilities)));
  }

  public ChannelType channelType() {
    return channelType;
  }

  public boolean supports(ChannelType channelType) {
    return this.channelType == channelType;
  }

  public Set<ChannelConnectionCapability> capabilities() {
    return capabilities;
  }

  public boolean supportsCapability(ChannelConnectionCapability capability) {
    return capabilities.contains(capability);
  }
}
