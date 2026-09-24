package com.messaging.ads.campaign.dto;

import com.messaging.ads.campaign.enums.CreativeVariantType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateCreativeVariantRequest(
    @NotNull CreativeVariantType variantType,
    @Positive Long campaignChannelId,
    @NotBlank @Size(max = 255) String name,
    @Size(max = 120) String placement,
    String primaryText,
    List<@Size(max = 500) String> headlines,
    List<@Size(max = 1000) String> descriptions,
    @Size(max = 80) String callToAction,
    @Size(max = 2048) String destinationUrl,
    String assetSpecJson) {}
