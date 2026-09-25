package com.messaging.ads.google.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record CreateGoogleResponsiveDisplayAdRequest(
    @NotBlank String name,
    @NotBlank String finalUrl,
    @NotBlank String businessName,
    List<String> headlines,
    @NotBlank String longHeadline,
    List<String> descriptions,
    @NotBlank String marketingImageBase64,
    @NotBlank String squareMarketingImageBase64,
    String squareLogoImageBase64) {}
