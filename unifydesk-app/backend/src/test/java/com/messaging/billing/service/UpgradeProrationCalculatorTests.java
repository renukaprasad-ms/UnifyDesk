package com.messaging.billing.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.messaging.billing.enums.BillingCycle;
import com.messaging.common.exception.BadRequestException;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class UpgradeProrationCalculatorTests {

  private final UpgradeProrationCalculator calculator = new UpgradeProrationCalculator();

  @Test
  void proratesUsingExactCurrentServicePeriod() {
    Instant start = Instant.parse("2026-01-01T00:00:00Z");
    Instant end = Instant.parse("2026-02-01T00:00:00Z");
    Instant effectiveAt = Instant.parse("2026-01-16T00:00:00Z");

    var quote =
        calculator.quote(
            new BigDecimal("1000"),
            new BigDecimal("2000"),
            BillingCycle.MONTHLY,
            start,
            end,
            effectiveAt,
            "INR");

    assertEquals(new BigDecimal("1032.26"), quote.targetProratedCharge());
    assertEquals(new BigDecimal("516.13"), quote.unusedCurrentCredit());
    assertEquals(new BigDecimal("516.13"), quote.amountDue());
  }

  @Test
  void crossCycleQuoteNormalizesTargetPriceByTargetCadence() {
    Instant start = Instant.parse("2026-01-01T00:00:00Z");
    Instant end = Instant.parse("2026-02-01T00:00:00Z");
    Instant effectiveAt = Instant.parse("2026-01-16T00:00:00Z");

    var quote =
        calculator.quote(
            new BigDecimal("1000"),
            new BigDecimal("12000"),
            BillingCycle.YEARLY,
            start,
            end,
            effectiveAt,
            "INR");

    assertEquals(new BigDecimal("526.03"), quote.targetProratedCharge());
    assertEquals(new BigDecimal("516.13"), quote.unusedCurrentCredit());
    assertEquals(new BigDecimal("9.90"), quote.amountDue());
  }

  @Test
  void rejectsNonInrR1Quote() {
    assertThrows(
        BadRequestException.class,
        () ->
            calculator.quote(
                BigDecimal.ONE,
                BigDecimal.TEN,
                BillingCycle.MONTHLY,
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-02-01T00:00:00Z"),
                Instant.parse("2026-01-15T00:00:00Z"),
                "USD"));
  }
}
