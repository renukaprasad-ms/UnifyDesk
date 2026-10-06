package com.messaging.billing.service;

import com.messaging.common.exception.BadRequestException;
import com.messaging.common.exception.ConflictException;
import java.time.Instant;

/** Canonical half-open effective-time rules for Billing R1 financial records. */
public final class FinancialEffectiveTime {

  private FinancialEffectiveTime() {}

  public static void requireExplicitRange(Instant from, Instant to, String subject) {
    if (from == null) {
      throw new BadRequestException(subject + " effectiveFrom is required");
    }
    if (to != null && !to.isAfter(from)) {
      throw new BadRequestException(subject + " effectiveTo must be after effectiveFrom");
    }
  }

  public static void assertStoredActiveRange(Instant from, Instant to, String subject) {
    if (from == null) {
      throw new ConflictException(
          "Malformed ACTIVE " + subject + " is missing required effectiveFrom");
    }
    if (to != null && !to.isAfter(from)) {
      throw new ConflictException(
          "Malformed ACTIVE " + subject + " has an invalid effective interval");
    }
  }

  public static boolean includes(Instant from, Instant to, Instant at) {
    return from != null
        && at != null
        && !from.isAfter(at)
        && (to == null || at.isBefore(to));
  }
}
