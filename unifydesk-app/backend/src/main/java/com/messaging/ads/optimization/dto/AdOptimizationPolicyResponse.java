package com.messaging.ads.optimization.dto;

import com.messaging.ads.enums.AdProvider;
import java.time.Instant;

public record AdOptimizationPolicyResponse(
    String id,
    String name,
    String policyType,
    boolean enabled,
    String mode,
    AdProvider provider,
    String adAccountId,
    String campaignId,
    String configJson,
    String status,
    Instant lastEvaluatedAt,
    Instant lastTriggeredAt,
    String lastResultJson,
    Instant createdAt,
    Instant updatedAt) {}
