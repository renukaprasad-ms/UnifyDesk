package com.messaging.broadcast.enums;

public enum BroadcastOutboxStatus {
  PENDING,
  PROCESSING,
  RETRY,
  PUBLISHED,
  FAILED
}
