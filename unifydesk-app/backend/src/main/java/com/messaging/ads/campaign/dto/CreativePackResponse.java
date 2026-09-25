package com.messaging.ads.campaign.dto;

import com.messaging.ads.campaign.entity.CreativePack;
import com.messaging.ads.campaign.entity.CreativePackAsset;
import com.messaging.ads.campaign.entity.CreativeVariant;
import java.time.Instant;
import java.util.List;

public record CreativePackResponse(
    String id,
    String campaignId,
    String name,
    String businessName,
    String offerText,
    String primaryMessage,
    String callToAction,
    String destinationUrl,
    String headlinesJson,
    String descriptionsJson,
    String createdByUserId,
    List<CreativePackAssetResponse> assets,
    List<CreativeVariantResponse> variants,
    Instant createdAt,
    Instant updatedAt) {
  public static CreativePackResponse from(
      CreativePack pack, List<CreativePackAsset> assets, List<CreativeVariant> variants) {
    return new CreativePackResponse(
        pack.getId().toString(),
        pack.getReachlyoCampaignId().toString(),
        pack.getName(),
        pack.getBusinessName(),
        pack.getOfferText(),
        pack.getPrimaryMessage(),
        pack.getCallToAction(),
        pack.getDestinationUrl(),
        pack.getHeadlinesJson(),
        pack.getDescriptionsJson(),
        pack.getCreatedByUserId().toString(),
        assets.stream().map(CreativePackAssetResponse::from).toList(),
        variants.stream().map(CreativeVariantResponse::from).toList(),
        pack.getCreatedAt(),
        pack.getUpdatedAt());
  }
}
