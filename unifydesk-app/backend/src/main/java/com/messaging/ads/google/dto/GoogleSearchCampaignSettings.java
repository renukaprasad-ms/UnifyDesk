package com.messaging.ads.google.dto;

import java.util.List;

public record GoogleSearchCampaignSettings(
    boolean targetSearchNetwork,
    boolean targetContentNetwork,
    String positiveGeoTargetType,
    boolean aiMaxEnabled,
    boolean textCustomizationEnabled,
    boolean finalUrlExpansionEnabled,
    List<String> termExclusions,
    List<String> messagingRestrictions) {

  public GoogleSearchCampaignSettings {
    termExclusions = termExclusions == null ? List.of() : List.copyOf(termExclusions);
    messagingRestrictions =
        messagingRestrictions == null ? List.of() : List.copyOf(messagingRestrictions);
  }

  public static GoogleSearchCampaignSettings defaults() {
    return new GoogleSearchCampaignSettings(
        true,
        false,
        "PRESENCE_OR_INTEREST",
        false,
        false,
        false,
        List.of(),
        List.of());
  }
}
