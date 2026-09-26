package com.messaging.ads.google.dto;

public record GoogleDemandGenChannelPerformance(
    String channel,
    long impressions,
    long clicks,
    long costMicros,
    double conversions,
    double conversionsValue) {}
