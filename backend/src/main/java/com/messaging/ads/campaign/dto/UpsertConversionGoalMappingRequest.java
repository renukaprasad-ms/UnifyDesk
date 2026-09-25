package com.messaging.ads.campaign.dto;

import com.messaging.ads.campaign.enums.ConversionTargetType;
import com.messaging.ads.enums.AdProvider;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record UpsertConversionGoalMappingRequest(
    @NotNull AdProvider provider,
    @NotNull @Positive Long adAccountId,
    @NotNull ConversionTargetType targetType,
    @Positive Long adConversionId,
    @Size(max = 120) String providerEventName) {}
