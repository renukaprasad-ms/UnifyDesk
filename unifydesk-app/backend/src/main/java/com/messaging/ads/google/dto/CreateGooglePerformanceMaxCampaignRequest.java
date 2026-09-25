package com.messaging.ads.google.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import java.util.List;

public record CreateGooglePerformanceMaxCampaignRequest(
    @NotBlank String name,
    @NotNull @Positive Long dailyBudgetMinor,
    @NotBlank String finalUrl,
    @NotBlank String businessName,
    List<String> headlines,
    @NotBlank String longHeadline,
    List<String> descriptions,
    @NotBlank String marketingImageBase64,
    @NotBlank String squareMarketingImageBase64,
    @NotBlank String logoImageBase64,
    String portraitMarketingImageBase64,
    String landscapeLogoImageBase64,
    List<String> youtubeVideoIds,
    String bidStrategy,
    @NotNull(message = "Choose whether this campaign contains EU political advertising") Boolean containsEuPoliticalAdvertising,
    LocalDate startDate,
    LocalDate endDate) {}
