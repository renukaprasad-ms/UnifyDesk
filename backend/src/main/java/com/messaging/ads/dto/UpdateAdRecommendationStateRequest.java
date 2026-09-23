package com.messaging.ads.dto;

import com.messaging.ads.enums.AdRecommendationLifecycleAction;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record UpdateAdRecommendationStateRequest(
    @NotNull AdRecommendationLifecycleAction action,
    Instant snoozedUntil) {}
