package com.messaging.ads.google.dto;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record UpdateGoogleCampaignRequest(
    @Size(max = 255) String name,
    @Positive Long dailyBudgetMinor,
    String bidStrategy,
    LocalDate startDate,
    LocalDate endDate) {}
