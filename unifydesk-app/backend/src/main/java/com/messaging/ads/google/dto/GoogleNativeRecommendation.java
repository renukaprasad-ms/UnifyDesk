package com.messaging.ads.google.dto;

public record GoogleNativeRecommendation(
    String resourceName,
    String type,
    boolean dismissed,
    String campaignResourceName,
    String adGroupResourceName,
    String impactJson) {}
