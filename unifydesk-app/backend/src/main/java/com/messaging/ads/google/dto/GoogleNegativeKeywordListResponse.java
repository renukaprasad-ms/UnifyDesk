package com.messaging.ads.google.dto;

import java.util.List;

public record GoogleNegativeKeywordListResponse(
    String resourceName,
    String id,
    String name,
    String status,
    long memberCount,
    long referenceCount,
    boolean attachedToCampaign,
    List<GoogleNegativeKeywordResponse> members) {}
