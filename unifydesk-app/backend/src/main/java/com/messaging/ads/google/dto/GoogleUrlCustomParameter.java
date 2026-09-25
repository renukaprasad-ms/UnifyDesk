package com.messaging.ads.google.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GoogleUrlCustomParameter(
    @NotBlank @Size(max = 16) String key,
    @NotBlank @Size(max = 200) String value) {}
