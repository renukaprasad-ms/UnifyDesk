package com.messaging.ads.google.dto;

import jakarta.validation.constraints.NotBlank;

public record GoogleAssetGroupStatusRequest(@NotBlank String status) {}
