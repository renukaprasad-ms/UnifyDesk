package com.messaging.ads.meta.dto;

import jakarta.validation.constraints.PositiveOrZero;

public record UpdateMetaSpendCapRequest(
    @PositiveOrZero Long spendCapMinor) {}
