package com.messaging.ads.google.dto;

public record GooglePerformanceMaxBrandGuidelinesRequest(
    Boolean enableBrandGuidelines,
    Boolean autoPopulateBrandAssets,
    String mainColor,
    String accentColor,
    String fontFamily) {}
