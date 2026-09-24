package com.messaging.ads.campaign.enums;

import com.messaging.ads.enums.AdProvider;

public enum CampaignChannelType {
  GOOGLE_SEARCH(AdProvider.GOOGLE_ADS),
  GOOGLE_PERFORMANCE_MAX(AdProvider.GOOGLE_ADS),
  GOOGLE_DEMAND_GEN(AdProvider.GOOGLE_ADS),
  GOOGLE_DISPLAY(AdProvider.GOOGLE_ADS),
  GOOGLE_SHOPPING(AdProvider.GOOGLE_ADS),
  META_ADS(AdProvider.META_ADS);

  private final AdProvider provider;

  CampaignChannelType(AdProvider provider) {
    this.provider = provider;
  }

  public AdProvider provider() {
    return provider;
  }
}
