package com.messaging.ads.google.dto;

import jakarta.validation.constraints.Positive;

public record UpdateGoogleKeywordRequest(
    @Positive Long cpcBidMinor,
    String status) {}
