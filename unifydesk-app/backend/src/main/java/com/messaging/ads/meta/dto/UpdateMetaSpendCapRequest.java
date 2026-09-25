package com.messaging.ads.meta.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record UpdateMetaSpendCapRequest(
    @NotNull @PositiveOrZero Long spendCapMinor) {}
