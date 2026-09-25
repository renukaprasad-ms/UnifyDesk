package com.messaging.ads.google.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record CreateGoogleCampaignRequest(
    @NotBlank @Size(max = 255) String name,
    @Positive Long dailyBudgetMinor,
    String bidStrategy,
    @NotNull(message = "Choose whether this campaign contains EU political advertising") Boolean containsEuPoliticalAdvertising,
    LocalDate startDate,
    LocalDate endDate) {}
