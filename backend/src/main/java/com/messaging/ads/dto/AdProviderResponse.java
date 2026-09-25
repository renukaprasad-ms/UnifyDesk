package com.messaging.ads.dto;

import com.messaging.ads.capability.AdFeatureCapabilityResponse;
import com.messaging.ads.enums.AdCapability;
import com.messaging.ads.enums.AdProvider;
import java.util.List;
import java.util.Set;

public record AdProviderResponse(
    AdProvider provider,
    String displayName,
    boolean configured,
    Set<AdCapability> capabilities,
    List<AdFeatureCapabilityResponse> featureCapabilities) {}
