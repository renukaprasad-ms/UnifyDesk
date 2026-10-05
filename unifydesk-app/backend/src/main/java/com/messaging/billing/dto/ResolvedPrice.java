package com.messaging.billing.dto;

import com.messaging.billing.enums.PricingSource;
import java.math.BigDecimal;

public record ResolvedPrice(
    BigDecimal unitPrice,
    String currency,
    PricingSource source,
    String pricingRuleId,
    String planId,
    String subscriptionId) {

  public ResolvedPrice(
      BigDecimal unitPrice,
      String currency,
      PricingSource source,
      String pricingRuleId,
      String planId) {
    this(unitPrice, currency, source, pricingRuleId, planId, null);
  }
}
