package com.messaging.ads.meta.dto;

public record MetaAdStudyResponse(
    String id,
    String name,
    String description,
    String type,
    Long startTime,
    Long endTime,
    Long observationEndTime,
    Long resultsFirstAvailableDate,
    String confidenceLevel,
    Long createdTime,
    Long updatedTime) {}
