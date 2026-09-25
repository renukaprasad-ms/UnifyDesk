package com.messaging.channel.gmail;

import java.time.Duration;

public class GmailRateLimitException extends RuntimeException {

  private final String reason;
  private final Duration retryAfter;

  public GmailRateLimitException(String reason, Duration retryAfter) {
    super(reason == null || reason.isBlank() ? "Gmail provider throttled the request" : reason);
    this.reason = reason;
    this.retryAfter = retryAfter == null ? Duration.ZERO : retryAfter;
  }

  public String reason() {
    return reason;
  }

  public Duration retryAfter() {
    return retryAfter;
  }
}
