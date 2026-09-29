package com.messaging.ads.google.dto;

public record GooglePerformanceMaxRetailSettingsResponse(
    boolean retail,
    String merchantId,
    String feedLabel) {}
