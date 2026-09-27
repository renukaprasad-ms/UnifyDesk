package com.messaging.ads.google.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record CreateGoogleExperimentRequest(
    @NotBlank @Size(max = 1024) String name,
    @Size(max = 2048) String description,
    @NotNull Long baseCampaignId,
    @Min(1) @Max(99) Integer treatmentTrafficPercent,
    @NotBlank String treatmentBidStrategy,
    @Size(max = 64) String suffix,
    LocalDate startDate,
    LocalDate endDate) {}
