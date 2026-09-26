package com.messaging.ads.google.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record GooglePriceOfferingInput(
    @NotBlank @Size(max = 25) String header,
    @NotBlank @Size(max = 25) String description,
    @DecimalMin("0.000001") BigDecimal amount,
    @NotBlank @Size(min = 3, max = 3) String currencyCode,
    @Size(max = 32) String unit,
    @NotBlank @Size(max = 2048) String finalUrl,
    @Size(max = 2048) String finalMobileUrl) {}
