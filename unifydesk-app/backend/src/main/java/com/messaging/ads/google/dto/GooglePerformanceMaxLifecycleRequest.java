package com.messaging.ads.google.dto;

import jakarta.validation.constraints.NotBlank;

public record GooglePerformanceMaxLifecycleRequest(
    @NotBlank String goalType,
    String targetOption,
    Double additionalValue,
    Double additionalHighLifetimeValue,
    Double valueMultiplier,
    Boolean enableBidAdjustmentsForLoyaltyMembers,
    Boolean showTargetedLoyaltyMemberBenefitsInPla) {}
