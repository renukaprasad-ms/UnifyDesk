package com.messaging.ads.meta.dto;

public record MetaDetailedTargetingSuggestion(
    String id,
    String name,
    String type,
    Long audienceSizeLowerBound,
    Long audienceSizeUpperBound) {}
