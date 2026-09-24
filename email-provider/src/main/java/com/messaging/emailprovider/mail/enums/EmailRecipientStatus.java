package com.messaging.emailprovider.mail.enums;

public enum EmailRecipientStatus {
  QUEUED,
  PROCESSING,
  DEFERRED,
  HANDED_OFF,
  REMOTE_DEFERRED,
  DELIVERED,
  BOUNCED,
  COMPLAINED,
  SUPPRESSED,
  FAILED
}
