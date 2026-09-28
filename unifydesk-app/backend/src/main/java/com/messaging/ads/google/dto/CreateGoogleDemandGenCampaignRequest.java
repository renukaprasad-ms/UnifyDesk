package com.messaging.ads.google.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;

public record CreateGoogleDemandGenCampaignRequest(
    @NotBlank String name,
    @NotNull @Positive Long dailyBudgetMinor,
    @NotBlank String adGroupName,
    @NotBlank String bidStrategy,
    @NotNull(message = "Choose whether this campaign contains EU political advertising")
        Boolean containsEuPoliticalAdvertising,
    LocalDate startDate,
    LocalDate endDate) {}
