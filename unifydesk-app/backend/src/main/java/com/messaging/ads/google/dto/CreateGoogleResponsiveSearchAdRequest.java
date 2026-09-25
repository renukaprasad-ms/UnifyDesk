package com.messaging.ads.google.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateGoogleResponsiveSearchAdRequest(
    @NotBlank @Size(max = 255) String name,
    @NotBlank String finalUrl,
    @Size(min = 3, max = 15) List<@NotBlank @Size(max = 30) String> headlines,
    @Size(min = 2, max = 4) List<@NotBlank @Size(max = 90) String> descriptions,
    @Size(max = 15) String path1,
    @Size(max = 15) String path2) {}
