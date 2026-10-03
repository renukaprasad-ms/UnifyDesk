package com.messaging.billing.service;

import com.messaging.billing.dto.UpgradeProrationQuote;
import com.messaging.common.exception.BadRequestException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class UpgradeProrationCalculator {

  private static final int INR_MINOR_SCALE = 2;

  public UpgradeProrationQuote quote(
      BigDecimal currentPrice,
      BigDecimal targetPrice,
      Instant currentPeriodStart,
      Instant currentPeriodEnd,
      Instant effectiveAt,
      String currency) {
    if (!"INR".equalsIgnoreCase(currency)) {
      throw new BadRequestException("Billing R1 supports INR only");
    }
    if (currentPrice == null || targetPrice == null) {
      throw new BadRequestException("Both current and target prices are required");
    }
    if (currentPeriodStart == null
        || currentPeriodEnd == null
        || !currentPeriodEnd.isAfter(currentPeriodStart)) {
      throw new BadRequestException("A valid current service period is required");
    }
    if (effectiveAt.isBefore(currentPeriodStart) || !effectiveAt.isBefore(currentPeriodEnd)) {
      throw new BadRequestException("Upgrade effective time must fall inside the current period");
    }

    BigDecimal fullNanos = BigDecimal.valueOf(Duration.between(currentPeriodStart, currentPeriodEnd).toNanos());
    BigDecimal remainingNanos = BigDecimal.valueOf(Duration.between(effectiveAt, currentPeriodEnd).toNanos());
    BigDecimal fraction = remainingNanos.divide(fullNanos, 18, RoundingMode.HALF_UP);

    BigDecimal targetCharge = money(targetPrice.multiply(fraction));
    BigDecimal currentCredit = money(currentPrice.multiply(fraction));
    BigDecimal net = money(targetCharge.subtract(currentCredit));
    BigDecimal amountDue = net.max(BigDecimal.ZERO).setScale(INR_MINOR_SCALE, RoundingMode.HALF_UP);
    BigDecimal creditAmount = net.min(BigDecimal.ZERO).abs().setScale(INR_MINOR_SCALE, RoundingMode.HALF_UP);

    return new UpgradeProrationQuote(
        "INR",
        effectiveAt,
        currentPeriodStart,
        currentPeriodEnd,
        money(currentPrice),
        money(targetPrice),
        targetCharge,
        currentCredit,
        net,
        amountDue,
        creditAmount);
  }

  private BigDecimal money(BigDecimal value) {
    return value.setScale(INR_MINOR_SCALE, RoundingMode.HALF_UP);
  }
}
