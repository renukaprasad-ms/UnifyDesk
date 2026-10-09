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
  UNIFYDESK_EMAIL,
  AZURE_EMAIL,
  SENDGRID,
  MSG91_EMAIL,
  MSG91_SMS,
  META_WHATSAPP,
  GMAIL,
  MICROSOFT,
  IMAP_SMTP,
  SMTP;

  public ChannelType channelType() {
    return switch (this) {
      case MSG91_SMS -> ChannelType.SMS;
      case META_WHATSAPP -> ChannelType.WHATSAPP;
      case AMAZON_SES, UNIFYDESK_EMAIL, AZURE_EMAIL, SENDGRID, MSG91_EMAIL, GMAIL, MICROSOFT, IMAP_SMTP, SMTP ->
          ChannelType.EMAIL;
    };
  }

  public boolean isBroadcastProvider() {
    return this == UNIFYDESK_EMAIL
        || this == AMAZON_SES
        || this == AZURE_EMAIL
        || this == SENDGRID
        || this == MSG91_EMAIL
        || this == MSG91_SMS
        || this == META_WHATSAPP;
  }

  public boolean isBulkProvider() {
    return this == AMAZON_SES
        || this == AZURE_EMAIL
        || this == SENDGRID
        || this == MSG91_EMAIL;
  }

  public boolean supports(ChannelType channelType) {
    return isBroadcastProvider() && channelType() == channelType;
  }
}
