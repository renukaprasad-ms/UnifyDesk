package com.messaging.channel.gmail;

import java.time.Duration;
import java.time.Instant;

public record GmailQuotaReservation(
    boolean allowed, Instant availableAt, Duration retryAfter, String reason) {

  public static GmailQuotaReservation allowed(Instant now) {
    return new GmailQuotaReservation(true, now, Duration.ZERO, "ok");
  }

  public static GmailQuotaReservation deferred(
      Instant now, Duration retryAfter, String reason) {
    Duration safeDelay =
        retryAfter == null || retryAfter.isNegative() ? Duration.ZERO : retryAfter;
    return new GmailQuotaReservation(false, now.plus(safeDelay), safeDelay, reason);
  }
}
