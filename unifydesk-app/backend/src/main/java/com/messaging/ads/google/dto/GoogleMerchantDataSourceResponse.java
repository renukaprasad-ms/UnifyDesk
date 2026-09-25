package com.messaging.ads.google.dto;

import java.util.List;

public record GoogleMerchantDataSourceResponse(
    String name,
    String dataSourceId,
    String displayName,
    String input,
    String contentLanguage,
    String feedLabel,
    List<String> countries,
    boolean apiManaged) {}
