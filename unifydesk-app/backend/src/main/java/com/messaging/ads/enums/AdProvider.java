package com.messaging.ads.enums;

public enum AdProvider {
  GOOGLE_ADS("Google Ads"),
  META_ADS("Meta Ads");

  private final String displayName;

  AdProvider(String displayName) {
    this.displayName = displayName;
  }

  public String displayName() {
    return displayName;
  }
}
