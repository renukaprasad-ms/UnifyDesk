package com.messaging.ads.meta.dto;

import jakarta.validation.constraints.NotBlank;

public record MetaAdStatusRequest(@NotBlank String status) {}
