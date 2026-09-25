package com.messaging.ads.google.dto;

import java.util.List;

public record GoogleInsightBreakdownsSyncResponse(
    int age,
    int gender,
    int country,
    int region,
    int publisherPlatform,
    int platformPosition,
    int impressionDevice,
    int devicePlatform,
    int conversionDestination,
    int actionType,
    int actionDevice,
    List<String> failedBreakdowns) {}
