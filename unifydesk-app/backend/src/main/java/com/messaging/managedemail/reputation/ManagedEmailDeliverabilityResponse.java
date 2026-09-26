package com.messaging.managedemail.reputation;

import java.math.BigDecimal;
import java.time.Instant;

public record ManagedEmailDeliverabilityResponse(
    Status status,
    Instant windowStart,
    Instant windowEnd,
    long accepted,
    long hardBounces,
    long complaints,
    BigDecimal hardBounceRate,
    BigDecimal complaintRate,
    boolean sendingRestricted,
    String notice) {

  public enum Status {
    INSUFFICIENT_DATA,
    HEALTHY,
    WARNING,
    THROTTLED,
    SUSPENDED
  }
}
