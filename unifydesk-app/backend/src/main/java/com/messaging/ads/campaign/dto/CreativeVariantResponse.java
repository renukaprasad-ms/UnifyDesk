package com.messaging.ads.campaign.dto;

import com.messaging.ads.campaign.entity.CreativeVariant;
import com.messaging.ads.campaign.enums.CreativeVariantOrigin;
import com.messaging.ads.campaign.enums.CreativeVariantStatus;
import com.messaging.ads.campaign.enums.CreativeVariantType;
import com.messaging.ads.enums.AdProvider;
import java.time.Instant;

public record CreativeVariantResponse(
    String id,
    String creativePackId,
    String campaignChannelId,
    AdProvider provider,
    CreativeVariantType variantType,
    CreativeVariantOrigin origin,
    CreativeVariantStatus status,
    String name,
    String placement,
    String primaryText,
    String headlinesJson,
    String descriptionsJson,
    String callToAction,
    String destinationUrl,
    String assetSpecJson,
    Instant createdAt,
    Instant updatedAt) {
  public static CreativeVariantResponse from(CreativeVariant variant) {
    return new CreativeVariantResponse(
        variant.getId().toString(),
        variant.getCreativePackId().toString(),
        variant.getCampaignChannelId() == null ? null : variant.getCampaignChannelId().toString(),
        variant.getProvider(),
        variant.getVariantType(),
        variant.getOrigin(),
        variant.getStatus(),
        variant.getName(),
        variant.getPlacement(),
        variant.getPrimaryText(),
        variant.getHeadlinesJson(),
        variant.getDescriptionsJson(),
        variant.getCallToAction(),
        variant.getDestinationUrl(),
        variant.getAssetSpecJson(),
        variant.getCreatedAt(),
        variant.getUpdatedAt());
  }
}
