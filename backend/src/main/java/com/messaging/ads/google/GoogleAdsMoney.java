package com.messaging.ads.google;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;

final class GoogleAdsMoney {

  private static final BigDecimal MICROS = BigDecimal.valueOf(1_000_000L);

  private GoogleAdsMoney() {}

  static Long minorToMicros(Long minor, String currencyCode) {
    if (minor == null) return null;
    int fraction = fractionDigits(currencyCode);
    BigDecimal minorScale = BigDecimal.TEN.pow(Math.max(0, fraction));
    return BigDecimal.valueOf(minor)
        .multiply(MICROS)
        .divide(minorScale, 0, RoundingMode.HALF_UP)
        .longValueExact();
  }

  static Long microsToMinor(Long micros, String currencyCode) {
    if (micros == null) return null;
    int fraction = fractionDigits(currencyCode);
    BigDecimal minorScale = BigDecimal.TEN.pow(Math.max(0, fraction));
    return BigDecimal.valueOf(micros)
        .multiply(minorScale)
        .divide(MICROS, 0, RoundingMode.HALF_UP)
        .longValue();
  }

  static BigDecimal microsToMajor(Long micros) {
    if (micros == null) return BigDecimal.ZERO;
    return BigDecimal.valueOf(micros).divide(MICROS, 6, RoundingMode.HALF_UP);
  }

  private static int fractionDigits(String currencyCode) {
    if (currencyCode == null || currencyCode.isBlank()) return 2;
    try {
      int digits = Currency.getInstance(currencyCode.trim().toUpperCase()).getDefaultFractionDigits();
      return digits < 0 ? 2 : Math.min(digits, 6);
    } catch (IllegalArgumentException ignored) {
      return 2;
    }
  }
}
