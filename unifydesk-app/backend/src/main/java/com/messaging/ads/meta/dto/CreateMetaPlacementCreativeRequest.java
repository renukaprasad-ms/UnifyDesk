package com.messaging.ads.meta.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateMetaPlacementCreativeRequest(
    @NotBlank @Size(max = 255) String name,
    @NotBlank String pageId,
    String instagramActorId,
    String callToActionType,
    String urlTags,
    @NotEmpty List<@Valid PlacementAsset> placementAssets) {

  public record PlacementAsset(
      @NotEmpty List<String> publisherPlatforms,
      List<String> facebookPositions,
      List<String> instagramPositions,
      List<String> messengerPositions,
      List<String> audienceNetworkPositions,
      String primaryText,
      String headline,
      String description,
      @NotBlank String destinationUrl,
      @NotBlank String mediaType,
      @NotBlank String mediaUrl) {}
}
