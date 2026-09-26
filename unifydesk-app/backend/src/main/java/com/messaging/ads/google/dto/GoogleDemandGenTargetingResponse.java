package com.messaging.ads.google.dto;

import java.util.List;

public record GoogleDemandGenTargetingResponse(
    String channelConfig,
    String channelStrategy,
    GoogleDemandGenSelectedChannels selectedChannels,
    boolean useAudienceGrouped,
    List<String> locationResources,
    List<String> excludedLocationResources,
    List<String> languageResources,
    List<String> audienceResources) {

  public GoogleDemandGenTargetingResponse {
    locationResources = locationResources == null ? List.of() : List.copyOf(locationResources);
    excludedLocationResources =
        excludedLocationResources == null ? List.of() : List.copyOf(excludedLocationResources);
    languageResources = languageResources == null ? List.of() : List.copyOf(languageResources);
    audienceResources = audienceResources == null ? List.of() : List.copyOf(audienceResources);
  }
}
