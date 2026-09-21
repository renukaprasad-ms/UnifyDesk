package com.messaging.observability.webhook;

public enum WebhookCallbackStatus {
  RECEIVED,
  PROCESSED,
  IGNORED,
  FAILED,
  REJECTED
}
