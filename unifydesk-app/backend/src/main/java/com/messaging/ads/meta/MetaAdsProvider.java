package com.messaging.ads.meta;

import com.messaging.ads.enums.AdCapability;
import com.messaging.ads.enums.AdProvider;
import com.messaging.ads.provider.AdsProvider;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MetaAdsProvider implements AdsProvider {

  private final MetaAdsProperties properties;

  @Override
  public AdProvider provider() {
    return AdProvider.META_ADS;
  }

  @Override
  public boolean configured() {
    return properties.configured();
  }

  @Override
  public Set<AdCapability> capabilities() {
    return Set.of(
        AdCapability.ACCOUNT_DISCOVERY,
        AdCapability.CAMPAIGN_MANAGEMENT,
        AdCapability.CREATIVE_MANAGEMENT,
        AdCapability.AUDIENCE_MANAGEMENT,
        AdCapability.CONVERSION_MANAGEMENT,
        AdCapability.ANALYTICS,
        AdCapability.BILLING_READ,
        AdCapability.BILLING_WRITE,
        AdCapability.RECOMMENDATIONS);
  }
}
