package com.messaging.ads.optimization.dto;

import com.messaging.ads.enums.AdProvider;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdOptimizationPolicyRequest(
    @NotBlank @Size(max = 255) String name,
    @NotBlank String policyType,
    @NotBlank String mode,
    AdProvider provider,
    Long adAccountId,
    Long campaignId,
    @NotBlank String configJson,
    Boolean enabled) {}
