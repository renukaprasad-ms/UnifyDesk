package com.messaging.ads.google.dto;

public record GooglePerformanceMaxLifecycleResponse(
    String resourceName,
    String goalResourceName,
    String goalId,
    String goalType,
    String targetOption,
    Double additionalValue,
    Double additionalHighLifetimeValue,
    Double valueMultiplier,
    Double highLifetimeValueMultiplier,
    Boolean enableBidAdjustmentsForLoyaltyMembers,
    Boolean showTargetedLoyaltyMemberBenefitsInPla) {}
