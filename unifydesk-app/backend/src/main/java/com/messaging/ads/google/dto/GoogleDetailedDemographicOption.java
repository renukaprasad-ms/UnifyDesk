package com.messaging.ads.google.dto;

public record GoogleDetailedDemographicOption(
    String id,
    String resourceName,
    String name,
    String parentResourceName,
    boolean launchedToAll,
    boolean searchAvailable) {}
