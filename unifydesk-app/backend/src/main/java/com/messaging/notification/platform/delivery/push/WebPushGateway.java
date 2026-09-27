package com.messaging.notification.platform.delivery.push;

import java.time.Duration;

public interface WebPushGateway {

  PushSendResult send(
      String endpoint, String p256dh, String auth, String payload, Duration ttl, String urgency);

  record PushSendResult(Outcome outcome, Integer statusCode, Duration retryAfter, String errorCode) {
    public enum Outcome {
      ACCEPTED,
      EXPIRED,
      RATE_LIMITED,
      TRANSIENT_FAILURE,
      PERMANENT_FAILURE
    }
  }
}
