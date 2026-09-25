package com.messaging.ads.google.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateGoogleAdvancedCampaignRequest(
    @NotBlank String name,
    @NotBlank String channelType,
    @NotNull @Positive Long dailyBudgetMinor,
    String bidStrategy,
    Long targetCpaMinor,
    BigDecimal targetRoas,
    String merchantId,
    Integer campaignPriority,
    Boolean enableLocal,
    @NotNull(message = "Choose whether this campaign contains EU political advertising") Boolean containsEuPoliticalAdvertising,
    LocalDate startDate,
    LocalDate endDate) {}
