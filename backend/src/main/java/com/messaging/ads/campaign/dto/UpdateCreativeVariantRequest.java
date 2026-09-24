package com.messaging.ads.campaign.dto;

import com.messaging.ads.campaign.enums.CreativeVariantOrigin;
import com.messaging.ads.campaign.enums.CreativeVariantStatus;
import jakarta.validation.constraints.Size;
import java.util.List;

public record UpdateCreativeVariantRequest(
    Long campaignChannelId,
    CreativeVariantOrigin origin,
    CreativeVariantStatus status,
    @Size(min = 1, max = 255) String name,
    @Size(max = 120) String placement,
    String primaryText,
    List<@Size(max = 500) String> headlines,
    List<@Size(max = 1000) String> descriptions,
    @Size(max = 80) String callToAction,
    @Size(max = 2048) String destinationUrl,
    String assetSpecJson) {}
