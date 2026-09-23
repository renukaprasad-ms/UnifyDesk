package com.messaging.ads.meta.dto;

import jakarta.validation.constraints.NotBlank;

public record MetaAdSetStatusRequest(@NotBlank String status) {}
