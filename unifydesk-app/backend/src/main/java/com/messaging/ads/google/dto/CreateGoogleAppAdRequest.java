package com.messaging.ads.google.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateGoogleAppAdRequest(
    @NotBlank @Size(max = 255) String name,
    @NotEmpty @Size(max = 5) List<@NotBlank @Size(max = 30) String> headlines,
    @NotEmpty @Size(max = 5) List<@NotBlank @Size(max = 90) String> descriptions,
    @Size(max = 20) List<@NotBlank @Size(max = 255) String> imageAssetResourceNames,
    @Size(max = 20) List<@NotBlank @Size(max = 255) String> videoAssetResourceNames) {}
