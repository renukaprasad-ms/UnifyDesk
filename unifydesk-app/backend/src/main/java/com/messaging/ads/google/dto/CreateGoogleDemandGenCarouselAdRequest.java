package com.messaging.ads.google.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record CreateGoogleDemandGenCarouselAdRequest(
    @NotBlank String name,
    @NotBlank String finalUrl,
    @NotBlank String businessName,
    @NotBlank String headline,
    @NotBlank String description,
    String callToActionText,
    @NotBlank String logoImageBase64,
    @NotEmpty List<@Valid GoogleDemandGenCarouselCardRequest> cards) {}
