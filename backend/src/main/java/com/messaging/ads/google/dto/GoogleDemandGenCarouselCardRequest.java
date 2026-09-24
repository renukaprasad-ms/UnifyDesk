package com.messaging.ads.google.dto;

import jakarta.validation.constraints.NotBlank;

public record GoogleDemandGenCarouselCardRequest(
    @NotBlank String headline,
    String callToActionText,
    String marketingImageBase64,
    String squareMarketingImageBase64,
    String portraitMarketingImageBase64) {}
