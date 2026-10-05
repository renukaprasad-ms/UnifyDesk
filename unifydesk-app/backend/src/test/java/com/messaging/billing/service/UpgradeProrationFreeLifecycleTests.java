package com.messaging.billing.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.messaging.billing.enums.BillingCycle;
import com.messaging.common.exception.BadRequestException;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class UpgradeProrationFreeLifecycleTests {

  private final UpgradeProrationCalculator calculator = new UpgradeProrationCalculator();

  @Test
  void freeToPaidMonthlyChargesFullTargetWithZeroCredit() {
    Instant requestedAt = Instant.parse("2026-10-04T06:00:00Z");
    var quote =
        calculator.quoteFromFree(
            BigDecimal.ZERO,
            new BigDecimal("999.00"),
            BillingCycle.MONTHLY,
            requestedAt,
            "INR");

    assertEquals(new BigDecimal("0.00"), quote.currentPrice());
    assertEquals(new BigDecimal("999.00"), quote.targetProratedCharge());
    assertEquals(new BigDecimal("0.00"), quote.unusedCurrentCredit());
    assertEquals(new BigDecimal("999.00"), quote.amountDue());
    assertEquals(Instant.parse("2026-11-04T06:00:00Z"), quote.currentPeriodEnd());
  }

  @Test
  void freeToPaidYearlyChargesFullTargetWithZeroCredit() {
    Instant requestedAt = Instant.parse("2026-10-04T06:00:00Z");
    var quote =
        calculator.quoteFromFree(
            BigDecimal.ZERO,
            new BigDecimal("9999.00"),
            BillingCycle.YEARLY,
            requestedAt,
            "INR");

    assertEquals(new BigDecimal("9999.00"), quote.amountDue());
    assertEquals(new BigDecimal("0.00"), quote.creditAmount());
    assertEquals(Instant.parse("2027-10-04T06:00:00Z"), quote.currentPeriodEnd());
  }

  @Test
  void freeToFreeCannotMasqueradeAsPaidUpgrade() {
    assertThrows(
        BadRequestException.class,
        () ->
            calculator.quoteFromFree(
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BillingCycle.FREE,
                Instant.parse("2026-10-04T06:00:00Z"),
                "INR"));
  }
}
