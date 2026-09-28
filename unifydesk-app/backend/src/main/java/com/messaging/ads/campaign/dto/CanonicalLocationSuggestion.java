package com.messaging.ads.campaign.dto;

import com.messaging.ads.enums.AdProvider;
import java.util.Set;

public record CanonicalLocationSuggestion(
    String key,
    String displayName,
    String type,
    String countryCode,
    String region,
    String city,
    String postalCode,
    Set<AdProvider> supportedProviders) {}
