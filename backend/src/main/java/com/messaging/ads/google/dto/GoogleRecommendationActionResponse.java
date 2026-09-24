package com.messaging.ads.google.dto;

import java.util.List;

public record GoogleRecommendationActionResponse(
    String action,
    int processedCount,
    List<String> resourceNames) {}
