package com.messaging.ads.meta.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateMetaAdRequest(
    @NotBlank String name,
    @NotBlank String creativeId) {}
