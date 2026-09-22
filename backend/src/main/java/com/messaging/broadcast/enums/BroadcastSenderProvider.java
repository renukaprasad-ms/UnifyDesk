package com.messaging.broadcast.enums;

/**
 * Delivery providers used by Broadcast.
 *
 * <p>The mailbox provider values are retained only so historical rows created before the SES-only
 * broadcast migration remain readable. New BroadcastSender records must use a bulk provider.
 */
public enum BroadcastSenderProvider {
  AMAZON_SES,
  AZURE_EMAIL,
  SENDGRID,
  GMAIL,
  MICROSOFT,
  IMAP_SMTP,
  SMTP;

  public boolean isBulkProvider() {
    return this == AMAZON_SES || this == AZURE_EMAIL || this == SENDGRID;
  }
}
