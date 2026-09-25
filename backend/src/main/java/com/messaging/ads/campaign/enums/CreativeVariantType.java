package com.messaging.ads.campaign.enums;

import com.messaging.ads.enums.AdProvider;

public enum CreativeVariantType {
  GOOGLE_RSA(AdProvider.GOOGLE_ADS),
  GOOGLE_DISPLAY(AdProvider.GOOGLE_ADS),
  GOOGLE_PERFORMANCE_MAX(AdProvider.GOOGLE_ADS),
  GOOGLE_DEMAND_GEN(AdProvider.GOOGLE_ADS),
  META_FEED(AdProvider.META_ADS),
  META_STORY(AdProvider.META_ADS),
  META_REEL(AdProvider.META_ADS),
  META_CAROUSEL(AdProvider.META_ADS),
  META_WHATSAPP_STATUS(AdProvider.META_ADS);

  private final AdProvider provider;

  CreativeVariantType(AdProvider provider) {
    this.provider = provider;
  }

  public AdProvider provider() {
    return provider;
  }
}
