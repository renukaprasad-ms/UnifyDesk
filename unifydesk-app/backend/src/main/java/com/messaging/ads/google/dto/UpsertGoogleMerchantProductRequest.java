package com.messaging.ads.google.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.util.List;

public record UpsertGoogleMerchantProductRequest(
    @NotBlank String dataSourceName,
    @NotBlank String offerId,
    @NotBlank String contentLanguage,
    @NotBlank String feedLabel,
    @NotBlank String title,
    String description,
    @NotBlank String link,
    @NotBlank String imageLink,
    @NotBlank String availability,
    @NotBlank String condition,
    String brand,
    @DecimalMin(value = "0.0", inclusive = false) BigDecimal price,
    String currency,
    List<String> gtins,
    String googleProductCategory) {}
