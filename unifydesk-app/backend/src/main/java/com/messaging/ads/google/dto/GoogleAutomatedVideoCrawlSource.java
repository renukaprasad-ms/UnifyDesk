package com.messaging.ads.google.dto;

import jakarta.validation.constraints.NotBlank;

public record GoogleAutomatedVideoCrawlSource(
    @NotBlank String url,
    @NotBlank String sourcePlatform,
    Boolean enabled) {}
