package com.messaging.ads.google.dto;

import jakarta.validation.constraints.NotBlank;

public record DeleteGoogleMerchantProductRequest(
    @NotBlank String dataSourceName,
    @NotBlank String contentLanguage,
    @NotBlank String feedLabel,
    @NotBlank String offerId) {}
