package com.messaging.ads.meta.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record CreateMetaConversionRequest(
    @NotBlank @Size(max=255) String name,
    String description,
    @NotBlank String customEventType,
    @NotBlank String pixelId,
    @NotBlank String ruleJson,
    @PositiveOrZero BigDecimal defaultConversionValue) {}
