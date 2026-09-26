package com.messaging.ads.google.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record GoogleAssetGenerationImageRequest(
    @NotBlank String advertisingChannelType,
    List<@NotBlank String> assetFieldTypes,
    @NotBlank String generationMode,
    @Size(max = 2048) String finalUrl,
    @Size(max = 1500) String freeformPrompt) {}
