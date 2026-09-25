package com.messaging.ads.campaign.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CompileReachlyoCampaignRequest(
    @NotNull @Positive Long adAccountId,
    @Positive Long creativePackId,
    @Positive Long audienceDefinitionId) {}
