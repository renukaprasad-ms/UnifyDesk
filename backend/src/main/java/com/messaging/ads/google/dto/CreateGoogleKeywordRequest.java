package com.messaging.ads.google.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateGoogleKeywordRequest(
    @NotBlank @Size(max = 500) String text,
    @NotBlank String matchType,
    @Positive Long cpcBidMinor,
    boolean negative) {}
