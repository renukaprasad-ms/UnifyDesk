package com.messaging.ads.google.dto;

public record GooglePerformanceMaxListingGroupResponse(
    String id,
    String resourceName,
    String parentResourceName,
    String type,
    String listingSource,
    String dimensionType,
    String dimensionValue,
    String dimensionLevel,
    String customAttributeIndex,
    Long impressions,
    Long clicks,
    Double conversions,
    Long costMicros) {}
