package com.messaging.ads.google.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record CreateGoogleConversionRequest(
    @NotBlank @Size(max = 255) String name,
    @NotBlank String category,
    String type,
    @PositiveOrZero BigDecimal defaultValue,
    String countingType) {}
