package com.messaging.ads.google.dto;

import java.util.List;

public record GoogleAssetGroupSignalResponse(
    String resourceName,
    String type,
    String value,
    String approvalStatus,
    List<String> disapprovalReasons) {}
