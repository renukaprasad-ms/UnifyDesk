package com.messaging.ads.google.dto;

import com.messaging.ads.campaign.dto.AudienceLocationInput;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record CreateGoogleAppCampaignRequest(
    @NotBlank @Size(max = 255) String name,
    @NotBlank String advertisingChannelSubType,
    @NotBlank @Size(max = 255) String appId,
    @NotBlank String appStore,
    @NotBlank String biddingStrategyGoalType,
    @NotNull @Positive Long dailyBudgetMinor,
    @Positive Long targetCpaMinor,
    @Positive BigDecimal targetRoas,
    @Size(max = 20) List<@Size(max = 255) String> conversionActionIds,
    @Valid @Size(max = 50) List<AudienceLocationInput> locations,
    @Valid @Size(max = 50) List<AudienceLocationInput> excludedLocations,
    @Size(max = 20) List<@Size(max = 100) String> languages,
    @NotNull(message = "Choose whether this campaign contains EU political advertising")
        Boolean containsEuPoliticalAdvertising,
    LocalDate startDate,
    LocalDate endDate) {}
