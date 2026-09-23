package com.messaging.ads.meta.dto;

import jakarta.validation.constraints.NotBlank;

public record MetaCampaignStatusRequest(@NotBlank String status) {}
