package com.messaging.ads.google.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GoogleNegativeKeywordInput(
    @NotBlank @Size(max = 80) String text,
    @NotBlank String matchType) {}
