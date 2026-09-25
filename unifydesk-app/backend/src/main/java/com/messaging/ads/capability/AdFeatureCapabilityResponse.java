package com.messaging.ads.capability;

import java.util.Set;

public record AdFeatureCapabilityResponse(
    AdFeatureCapability feature,
    AdFeatureAvailability availability,
    boolean implemented,
    boolean supported,
    Boolean accountEligible,
    boolean requiresManagedAccount,
    String reason,
    String apiVersion,
    Set<String> requiredScopes) {}
