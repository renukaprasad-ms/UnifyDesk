package com.messaging.ads.optimization.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record AdOptimizationPolicyEvaluation(
    String policyId,
    boolean triggered,
    String state,
    String metric,
    BigDecimal observedValue,
    BigDecimal threshold,
    String action,
    boolean executed,
    boolean approvalRequired,
    String message,
    Instant evaluatedAt) {}
