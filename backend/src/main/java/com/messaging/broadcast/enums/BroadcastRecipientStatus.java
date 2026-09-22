package com.messaging.broadcast.enums;

public enum BroadcastRecipientStatus {
  PENDING,
  PROCESSING,
  RETRY,
  ACCEPTED_BY_PROVIDER,
  FAILED,
  BOUNCED,
  UNKNOWN,
  SKIPPED
}
