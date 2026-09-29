package com.messaging.ads.google.dto;

public record GoogleLifecycleGoalResponse(
    String resourceName,
    String goalId,
    String goalType,
    Double additionalValue,
    Double additionalHighLifetimeValue,
    Double valueMultiplier,
    Double highLifetimeValueMultiplier) {}
