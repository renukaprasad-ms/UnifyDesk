package com.messaging.ads.google.dto;

import jakarta.validation.constraints.NotBlank;

public record GoogleLifecycleGoalUpsertRequest(
    @NotBlank String goalType,
    Double additionalValue,
    Double additionalHighLifetimeValue,
    Double valueMultiplier) {}
