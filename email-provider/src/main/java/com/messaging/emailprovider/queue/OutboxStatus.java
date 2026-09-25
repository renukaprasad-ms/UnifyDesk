package com.messaging.emailprovider.queue;

public enum OutboxStatus {
  PENDING,
  PROCESSING,
  PUBLISHED,
  FAILED
}
