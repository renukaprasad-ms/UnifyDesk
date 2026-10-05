package com.messaging.billing.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class CashfreePaymentGatewayMoneyTests {

  @Test
  void normalizesCanonicalSixDecimalInvoiceAmountToCashfreeCurrencyPrecision() {
    assertEquals(
        new BigDecimal("499.00"),
        CashfreePaymentGateway.cashfreeMoney(new BigDecimal("499.000000")));
  }

  @Test
  void roundsProviderAmountUsingHalfUpMinorUnits() {
    assertEquals(
        new BigDecimal("499.01"),
        CashfreePaymentGateway.cashfreeMoney(new BigDecimal("499.005000")));
  }

  @Test
  void rejectsMissingProviderAmount() {
    assertThrows(
        IllegalArgumentException.class,
        () -> CashfreePaymentGateway.cashfreeMoney(null));
  }
}
