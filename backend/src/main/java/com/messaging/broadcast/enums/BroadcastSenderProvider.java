package com.messaging.broadcast.enums;

import com.messaging.channel.enums.ChannelType;

/**
 * Delivery providers used by Broadcast.
 *
 * <p>The mailbox provider values are retained only so historical rows created before the bulk
 * provider migration remain readable. New BroadcastSender records must use a Broadcast provider.
 */
public enum BroadcastSenderProvider {
  AMAZON_SES,
  AZURE_EMAIL,
  SENDGRID,
  MSG91,
  GMAIL,
  MICROSOFT,
  IMAP_SMTP,
  SMTP;

  public ChannelType channelType() {
    return this == MSG91 ? ChannelType.SMS : ChannelType.EMAIL;
  }

  public boolean isBroadcastProvider() {
    return this == AMAZON_SES || this == AZURE_EMAIL || this == SENDGRID || this == MSG91;
  }

  public boolean isBulkProvider() {
    return this == AMAZON_SES || this == AZURE_EMAIL || this == SENDGRID;
  }

  public boolean supports(ChannelType channelType) {
    return isBroadcastProvider() && channelType() == channelType;
  }
}
