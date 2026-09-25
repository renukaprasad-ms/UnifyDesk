package com.messaging.ads.campaign.compiler;

import com.messaging.ads.capability.AdFeatureCapability;
import java.util.Map;
import java.util.Set;

public record ProviderPlanOperation(
    int sequence,
    String operationKey,
    ProviderPlanAction action,
    ProviderPlanResource resource,
    boolean createPaused,
    Set<AdFeatureCapability> requiredCapabilities,
    Map<String, Object> configuration) {}
