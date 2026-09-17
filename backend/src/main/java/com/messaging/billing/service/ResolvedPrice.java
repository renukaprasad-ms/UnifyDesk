package com.messaging.billing.service;

import com.messaging.billing.enums.PricingSource;
import java.math.BigDecimal;

public record ResolvedPrice(
    String planId,
    PricingSource source,
    String pricingRuleId,
    BigDecimal unitPrice,
    String currency) {}
