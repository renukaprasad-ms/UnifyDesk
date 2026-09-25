package com.messaging.ads.google.dto;

import jakarta.validation.constraints.NotBlank;

public record GoogleCampaignStatusRequest(@NotBlank String status) {}
