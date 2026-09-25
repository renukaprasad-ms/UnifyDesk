package com.messaging.ads.google.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateGoogleAudienceRequest(
    @NotBlank @Size(max = 255) String name,
    String description,
    @Min(0) @Max(540) Integer membershipLifeSpan,
    String uploadKeyType) {}
