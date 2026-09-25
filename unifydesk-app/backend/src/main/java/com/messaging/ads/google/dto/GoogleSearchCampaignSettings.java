package com.messaging.ads.google.dto;

public record GoogleSearchCampaignSettings(
    boolean targetSearchNetwork,
    boolean targetContentNetwork,
    String positiveGeoTargetType,
    boolean aiMaxEnabled,
    boolean textCustomizationEnabled,
    boolean finalUrlExpansionEnabled) {

  public static GoogleSearchCampaignSettings defaults() {
    return new GoogleSearchCampaignSettings(
        true,
        false,
        "PRESENCE_OR_INTEREST",
        false,
        false,
        false);
  }
}
