package com.messaging.ads.meta.dto;

public record MetaInsightsSyncResponse(
    int accountRows,
    int campaignRows,
    int adSetRows,
    int adRows) {}
