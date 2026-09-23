package com.messaging.ads.google.dto;

import java.util.List;

public record GoogleFullSyncResponse(
    int campaigns,
    int adGroups,
    int keywords,
    int ads,
    int audiences,
    int conversions,
    int analyticsRows,
    List<String> failedOperations) {}
