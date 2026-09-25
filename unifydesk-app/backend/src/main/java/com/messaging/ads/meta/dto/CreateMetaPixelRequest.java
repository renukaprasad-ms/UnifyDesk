package com.messaging.ads.meta.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateMetaPixelRequest(@NotBlank @Size(max = 255) String name) {}
