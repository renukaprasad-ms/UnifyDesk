package com.messaging.ads.provider;

import com.messaging.ads.enums.AdCapability;
import com.messaging.ads.enums.AdProvider;
import java.util.Set;

/**
 * Base contract for advertising providers.
 *
 * <p>Provider-specific OAuth, campaign, analytics, and billing operations should live in small
 * capability interfaces instead of growing this base contract. Google Ads and Meta Ads have
 * different object models, so the shared layer only describes capabilities that are genuinely
 * common.
 */
public interface AdsProvider {

  AdProvider provider();

  boolean configured();

  Set<AdCapability> capabilities();
}
