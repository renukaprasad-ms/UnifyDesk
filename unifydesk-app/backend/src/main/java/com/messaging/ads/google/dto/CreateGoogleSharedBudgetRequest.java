package com.messaging.ads.google.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateGoogleSharedBudgetRequest(
    @NotBlank String name,
    @NotNull @Positive Long amountMinor) {}
