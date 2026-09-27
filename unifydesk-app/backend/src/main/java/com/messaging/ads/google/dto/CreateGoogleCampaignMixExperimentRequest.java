package com.messaging.ads.google.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

public record CreateGoogleCampaignMixExperimentRequest(
    @NotBlank @Size(max = 1024) String name,
    @Size(max = 2048) String description,
    @NotEmpty List<Long> controlCampaignIds,
    @NotEmpty List<Long> treatmentCampaignIds,
    @Min(1) @Max(99) Integer treatmentTrafficPercent,
    LocalDate startDate,
    LocalDate endDate) {}
