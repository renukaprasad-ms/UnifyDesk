package com.messaging.ads.google.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record CreateGoogleDemandGenMultiAssetAdRequest(
    @NotBlank String name,
    @NotBlank String finalUrl,
    @NotBlank String businessName,
    @NotEmpty List<String> headlines,
    @NotEmpty List<String> descriptions,
    String callToActionText,
    String marketingImageBase64,
    String squareMarketingImageBase64,
    String portraitMarketingImageBase64,
    String tallPortraitMarketingImageBase64,
    @NotBlank String logoImageBase64) {}
