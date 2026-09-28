package com.messaging.ads.google.dto;

public record GoogleGeoLocationSuggestion(
    String resourceName,
    String name,
    String canonicalName,
    String targetType,
    String countryCode) {}
