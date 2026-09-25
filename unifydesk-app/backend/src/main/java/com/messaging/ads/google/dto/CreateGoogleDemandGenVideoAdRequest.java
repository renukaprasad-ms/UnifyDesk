package com.messaging.ads.google.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record CreateGoogleDemandGenVideoAdRequest(
    @NotBlank String name,
    @NotBlank String finalUrl,
    @NotBlank String businessName,
    List<String> headlines,
    List<String> longHeadlines,
    List<String> descriptions,
    @NotBlank String youtubeVideoId,
    @NotBlank String logoImageBase64) {}
