package com.messaging.ads.meta.dto;

public record MetaGeoLocationSuggestion(
    String key,
    String name,
    String type,
    String countryCode,
    String countryName,
    String region,
    String regionId,
    Boolean supportsRegion,
    Boolean supportsCity) {}
