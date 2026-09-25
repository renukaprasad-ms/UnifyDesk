package com.messaging.ads.campaign.dto;

import com.messaging.ads.campaign.enums.CreativeAssetRole;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record CreativePackAssetInput(
    @NotNull @Positive Long mediaId,
    @NotNull CreativeAssetRole role,
    @Size(max = 160) String label,
    @PositiveOrZero Integer sortOrder) {}
