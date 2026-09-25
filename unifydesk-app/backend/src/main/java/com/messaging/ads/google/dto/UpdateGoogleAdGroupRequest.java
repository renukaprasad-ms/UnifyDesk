package com.messaging.ads.google.dto;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record UpdateGoogleAdGroupRequest(
    @Size(max = 255) String name,
    @Positive Long cpcBidMinor) {}
