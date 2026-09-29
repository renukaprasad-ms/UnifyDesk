package com.messaging.ads.google.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateGooglePortfolioBiddingStrategyRequest(
    @NotBlank String name,
    @NotBlank String type,
    Long targetCpaMinor,
    Double targetRoas) {}
