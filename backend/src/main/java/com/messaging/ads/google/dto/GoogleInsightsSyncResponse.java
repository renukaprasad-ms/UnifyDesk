package com.messaging.ads.google.dto;

public record GoogleInsightsSyncResponse(
    int accountRows,
    int campaignRows,
    int adGroupRows,
    int adRows) {}
