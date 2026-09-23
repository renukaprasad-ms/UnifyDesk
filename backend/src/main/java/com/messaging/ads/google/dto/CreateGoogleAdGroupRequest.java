package com.messaging.ads.google.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateGoogleAdGroupRequest(
    @NotBlank @Size(max = 255) String name,
    @Positive Long cpcBidMinor) {}
