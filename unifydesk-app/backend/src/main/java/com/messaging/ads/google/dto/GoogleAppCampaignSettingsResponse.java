package com.messaging.ads.google.dto;

import java.util.List;

public record GoogleAppCampaignSettingsResponse(
    String advertisingChannelSubType,
    String appId,
    String appStore,
    String biddingStrategyGoalType,
    List<String> conversionActionResources,
    List<String> locationResources,
    List<String> excludedLocationResources,
    List<String> languageResources) {

  public GoogleAppCampaignSettingsResponse {
    conversionActionResources =
        conversionActionResources == null ? List.of() : List.copyOf(conversionActionResources);
    locationResources = locationResources == null ? List.of() : List.copyOf(locationResources);
    excludedLocationResources =
        excludedLocationResources == null ? List.of() : List.copyOf(excludedLocationResources);
    languageResources = languageResources == null ? List.of() : List.copyOf(languageResources);
  }
}
