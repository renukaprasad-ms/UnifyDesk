package com.messaging.ads.google.dto;

import java.util.List;

public record GoogleAssetGroupResponse(
    String id,
    String resourceName,
    String name,
    String status,
    String primaryStatus,
    String adStrength,
    List<String> finalUrls) {}
