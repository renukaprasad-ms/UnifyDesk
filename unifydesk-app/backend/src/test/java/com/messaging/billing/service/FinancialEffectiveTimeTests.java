package com.messaging.billing.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.messaging.common.exception.ConflictException;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class FinancialEffectiveTimeTests {

  @Test
  void halfOpenIntervalAcceptsExactStartAndRejectsExactEnd() {
    Instant from = Instant.parse("2026-10-01T00:00:00Z");
    Instant to = Instant.parse("2026-11-01T00:00:00Z");

    assertTrue(FinancialEffectiveTime.includes(from, to, from));
    assertFalse(FinancialEffectiveTime.includes(from, to, to));
  }

  @Test
  void futureAndExpiredRulesDoNotMatchNow() {
    Instant now = Instant.parse("2026-10-15T00:00:00Z");

    assertFalse(
        FinancialEffectiveTime.includes(
            Instant.parse("2026-10-16T00:00:00Z"), null, now));
    assertFalse(
        FinancialEffectiveTime.includes(
            Instant.parse("2026-09-01T00:00:00Z"),
            Instant.parse("2026-10-15T00:00:00Z"),
            now));
  }

  @Test
  void legacyActiveNullStartFailsClosedWithCommercialConflict() {
    assertThrows(
        ConflictException.class,
        () -> FinancialEffectiveTime.assertStoredActiveRange(null, null, "PlanPrice"));
  }
}
