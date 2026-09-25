package com.messaging.channel.gmail;

import java.time.Instant;

public class GmailRealtimeDeferredException extends RuntimeException {

  private final Instant availableAt;

  public GmailRealtimeDeferredException(String message, Instant availableAt) {
    super(message);
    this.availableAt = availableAt;
  }

  public Instant availableAt() {
    return availableAt;
  }
}
