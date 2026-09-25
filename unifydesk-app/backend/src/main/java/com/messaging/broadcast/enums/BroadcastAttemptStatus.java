package com.messaging.broadcast.enums;

public enum BroadcastAttemptStatus {
  PROCESSING,
  ACCEPTED,
  RETRYABLE_FAILURE,
  PERMANENT_FAILURE,
  UNKNOWN
}
