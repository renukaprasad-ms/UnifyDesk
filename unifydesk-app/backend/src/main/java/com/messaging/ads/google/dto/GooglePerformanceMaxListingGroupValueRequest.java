package com.messaging.ads.google.dto;

import jakarta.validation.constraints.NotBlank;

public record GooglePerformanceMaxListingGroupValueRequest(
    @NotBlank String value,
    String level,
    String customAttributeIndex,
    boolean included) {}
