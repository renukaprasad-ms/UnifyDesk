package com.messaging.ads.campaign.dto;

import com.messaging.ads.campaign.entity.CreativePackAsset;
import com.messaging.ads.campaign.enums.CreativeAssetRole;

public record CreativePackAssetResponse(
    String id, String mediaId, CreativeAssetRole role, String label, int sortOrder) {
  public static CreativePackAssetResponse from(CreativePackAsset asset) {
    return new CreativePackAssetResponse(
        asset.getId().toString(),
        asset.getMediaId().toString(),
        asset.getRole(),
        asset.getLabel(),
        asset.getSortOrder());
  }
}
