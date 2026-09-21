package com.messaging.channel.enums;

public enum ProviderType {
  GMAIL(ChannelType.EMAIL),
  MICROSOFT(ChannelType.EMAIL),
  IMAP_SMTP(ChannelType.EMAIL),
  SMTP(ChannelType.EMAIL),
  META_WHATSAPP(ChannelType.WHATSAPP),
  MSG91(ChannelType.SMS);

  private final ChannelType channelType;

  ProviderType(ChannelType channelType) {
    this.channelType = channelType;
  }

  public ChannelType channelType() {
    return channelType;
  }

  public boolean supports(ChannelType channelType) {
    return this.channelType == channelType;
  }
}
