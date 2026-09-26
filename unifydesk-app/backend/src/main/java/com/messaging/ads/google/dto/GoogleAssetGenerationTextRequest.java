package com.messaging.ads.google.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record GoogleAssetGenerationTextRequest(
    @NotBlank String advertisingChannelType,
    @NotEmpty List<@NotBlank String> assetFieldTypes,
    @NotBlank @Size(max = 2048) String finalUrl,
    @Size(max = 1500) String freeformPrompt,
    @Size(max = 100) List<@Size(max = 200) String> keywords) {}
