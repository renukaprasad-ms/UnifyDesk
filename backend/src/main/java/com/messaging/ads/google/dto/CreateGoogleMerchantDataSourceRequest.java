package com.messaging.ads.google.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record CreateGoogleMerchantDataSourceRequest(
    @NotBlank String displayName,
    @NotBlank String contentLanguage,
    @NotBlank String feedLabel,
    @NotEmpty List<@NotBlank String> countries) {}
